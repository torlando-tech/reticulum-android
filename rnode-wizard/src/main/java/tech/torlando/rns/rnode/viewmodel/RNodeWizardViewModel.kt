package tech.torlando.rns.rnode.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import tech.torlando.rns.rnode.ble.BlePairingHandler
import tech.torlando.rns.rnode.data.BluetoothType
import tech.torlando.rns.rnode.data.CommunitySlots
import tech.torlando.rns.rnode.data.DeviceClassifier
import tech.torlando.rns.rnode.data.DeviceTypeCache
import tech.torlando.rns.rnode.data.DiscoveredRNode
import tech.torlando.rns.rnode.data.DiscoveredUsbDevice
import tech.torlando.rns.rnode.data.FrequencyRegion
import tech.torlando.rns.rnode.data.FrequencyRegions
import tech.torlando.rns.rnode.data.FrequencySlotCalculator
import tech.torlando.rns.rnode.data.RNodeWizardResult
import tech.torlando.rns.rnode.data.ModemPreset
import tech.torlando.rns.rnode.data.RNodeRegionalPreset
import tech.torlando.rns.rnode.data.RNodeRegionalPresets
import tech.torlando.rns.bridges.usb.KotlinUSBBridge
import tech.torlando.rns.rnode.util.DeviceNameValidator
import tech.torlando.rns.rnode.util.RssiThrottler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.regex.Pattern

/**
 * Wizard step enumeration.
 */
enum class WizardStep {
    DEVICE_DISCOVERY,
    REGION_SELECTION,
    MODEM_PRESET,
    FREQUENCY_SLOT,
    REVIEW_CONFIGURE,
}

/**
 * Connection type for RNode devices.
 */
enum class RNodeConnectionType {
    BLUETOOTH, // Classic or BLE Bluetooth
    TCP_WIFI, // TCP over WiFi
    USB_SERIAL, // USB Serial (CDC-ACM, FTDI, CP210x, CH340, etc.)
}

/** Default interface name - used to detect if user has customized it */
private const val DEFAULT_INTERFACE_NAME = "RNode LoRa"

/**
 * Regulatory limits for a frequency region.
 * Used to validate user input against regional regulations.
 */
data class RegionLimits(
    val maxTxPower: Int,
    val minFrequency: Long,
    val maxFrequency: Long,
    val dutyCycle: Int,
)

/**
 * State for the RNode setup wizard.
 */
@androidx.compose.runtime.Immutable
data class RNodeWizardState(
    // Wizard navigation
    val currentStep: WizardStep = WizardStep.DEVICE_DISCOVERY,
    // Step 1: Device Discovery
    val connectionType: RNodeConnectionType = RNodeConnectionType.BLUETOOTH,
    val isScanning: Boolean = false,
    val discoveredDevices: List<DiscoveredRNode> = emptyList(),
    val selectedDevice: DiscoveredRNode? = null,
    val scanError: String? = null,
    val showManualEntry: Boolean = false,
    val manualDeviceName: String = "",
    val manualDeviceNameError: String? = null,
    val manualDeviceNameWarning: String? = null,
    val manualBluetoothType: BluetoothType = BluetoothType.CLASSIC,
    val isPairingInProgress: Boolean = false,
    val pairingError: String? = null,
    val pairingTimeRemaining: Int = 0,
    val lastPairingDeviceAddress: String? = null,
    val isWaitingForReconnect: Boolean = false,
    val reconnectDeviceName: String? = null,
    // TCP/WiFi connection fields
    val tcpHost: String = "",
    val tcpPort: String = "7633",
    val isTcpValidating: Boolean = false,
    val tcpValidationSuccess: Boolean? = null,
    val tcpValidationError: String? = null,
    // USB Serial connection fields
    val usbDevices: List<DiscoveredUsbDevice> = emptyList(),
    val selectedUsbDevice: DiscoveredUsbDevice? = null,
    val isUsbScanning: Boolean = false,
    val usbScanError: String? = null,
    val isRequestingUsbPermission: Boolean = false,
    // Bluetooth pairing via USB mode
    val isUsbPairingMode: Boolean = false,
    val usbBluetoothPin: String? = null,
    val usbPairingStatus: String? = null,
    val showManualPinEntry: Boolean = false,
    val manualPinInput: String = "",
    // USB-assisted Bluetooth pairing (from Bluetooth tab)
    val isUsbAssistedPairingActive: Boolean = false,
    val usbAssistedPairingDevices: List<DiscoveredRNode> = emptyList(),
    val usbAssistedPairingPin: String? = null,
    val usbAssistedPairingStatus: String? = null,
    // Companion Device Association (Android 12+)
    val isAssociating: Boolean = false,
    val pendingAssociationIntent: IntentSender? = null,
    val associationError: String? = null,
    // Step 2: Region/Frequency Selection
    val searchQuery: String = "",
    val selectedCountry: String? = null,
    // Legacy: popular local presets
    val selectedPreset: RNodeRegionalPreset? = null,
    // New: frequency band selection
    val selectedFrequencyRegion: FrequencyRegion? = null,
    val isCustomMode: Boolean = false,
    // Collapsible section for local presets
    val showPopularPresets: Boolean = false,
    // Step 3: Modem Preset Selection
    val selectedModemPreset: ModemPreset = ModemPreset.DEFAULT,
    // Step 4: Frequency Slot Selection
    // Default Meshtastic slot
    val selectedSlot: Int = 20,
    // Set when a preset is selected that doesn't align with slots
    val customFrequency: Long? = null,
    // The preset selected on slot page
    val selectedSlotPreset: RNodeRegionalPreset? = null,
    // Step 5: Review & Configure
    val interfaceName: String = DEFAULT_INTERFACE_NAME,
    // US default
    val frequency: String = "914875000",
    // Long Fast default
    val bandwidth: String = "250000",
    // Long Fast default
    val spreadingFactor: String = "11",
    // Long Fast default (4/5)
    val codingRate: String = "5",
    // Safe default for all devices
    val txPower: String = "17",
    val stAlock: String = "",
    val ltAlock: String = "",
    val interfaceMode: String = "boundary",
    val showAdvancedSettings: Boolean = false,
    // Validation errors
    val nameError: String? = null,
    val frequencyError: String? = null,
    val bandwidthError: String? = null,
    val txPowerError: String? = null,
    val spreadingFactorError: String? = null,
    val codingRateError: String? = null,
    val stAlockError: String? = null,
    val ltAlockError: String? = null,
    // Regulatory warning
    val showRegulatoryWarning: Boolean = false,
    val regulatoryWarningMessage: String? = null,
    // Save state
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
    // Saved configuration result
    val savedResult: RNodeWizardResult? = null,
)

/**
 * ViewModel for the RNode setup wizard.
 */
@Suppress("LargeClass")
class RNodeWizardViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "RNodeWizardVM"
        private val NUS_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private const val SCAN_DURATION_MS = 10000L
        private const val PREFS_NAME = "rnode_device_types"
        private const val KEY_DEVICE_TYPES = "device_type_cache"
        private const val PAIRING_START_TIMEOUT_MS = 5_000L
        private const val PIN_ENTRY_TIMEOUT_MS = 60_000L
        private const val RECONNECT_SCAN_TIMEOUT_MS = 15_000L
        private const val RSSI_UPDATE_INTERVAL_MS = 3000L
        private const val TCP_CONNECTION_TIMEOUT_MS = 5000
    }

    private val context: Context get() = getApplication<Application>()

    private val _state = MutableStateFlow(RNodeWizardState())
    val state: StateFlow<RNodeWizardState> = _state.asStateFlow()

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // USB bridge (singleton)
    private val usbBridge by lazy {
        KotlinUSBBridge.getInstance(context)
    }

    // RSSI update throttling - prevent excessive UI updates
    private val rssiThrottler = RssiThrottler(intervalMs = RSSI_UPDATE_INTERVAL_MS)

    // Device type cache - persists detected BLE vs Classic types
    private val deviceTypePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Device classifier for determining BLE vs Classic device types
    private val deviceClassifier =
        DeviceClassifier(
            deviceTypeCache =
                object : DeviceTypeCache {
                    override fun getCachedType(address: String): BluetoothType? {
                        val json = deviceTypePrefs.getString(KEY_DEVICE_TYPES, "{}") ?: "{}"
                        return try {
                            val jsonObj = org.json.JSONObject(json)
                            if (!jsonObj.has(address)) return null
                            when (jsonObj.optString(address)) {
                                "CLASSIC" -> BluetoothType.CLASSIC
                                "BLE" -> BluetoothType.BLE
                                else -> null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to read device type cache", e)
                            null
                        }
                    }

                    override fun cacheType(
                        address: String,
                        type: BluetoothType,
                    ) {
                        try {
                            val json = deviceTypePrefs.getString(KEY_DEVICE_TYPES, "{}") ?: "{}"
                            val obj = org.json.JSONObject(json)
                            obj.put(address, type.name)
                            deviceTypePrefs.edit().putString(KEY_DEVICE_TYPES, obj.toString()).apply()
                            Log.d(TAG, "Cached device type: $address -> $type")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to cache device type", e)
                        }
                    }
                },
        )

    // Track user-modified fields to preserve state during navigation
    private val userModifiedFields = mutableSetOf<String>()

    // ========== NAVIGATION ==========

    fun goToStep(step: WizardStep) {
        _state.update { it.copy(currentStep = step) }
    }

    fun goToNextStep() {
        val currentState = _state.value
        val nextStep =
            when (currentState.currentStep) {
                WizardStep.DEVICE_DISCOVERY -> WizardStep.REGION_SELECTION
                WizardStep.REGION_SELECTION -> {
                    if (currentState.isCustomMode) {
                        _state.update { it.copy(showAdvancedSettings = true) }
                        WizardStep.REVIEW_CONFIGURE
                    } else if (currentState.selectedPreset != null) {
                        _state.update { it.copy(showAdvancedSettings = true) }
                        WizardStep.REVIEW_CONFIGURE
                    } else {
                        applyFrequencyRegionSettings()
                        WizardStep.MODEM_PRESET
                    }
                }
                WizardStep.MODEM_PRESET -> {
                    applyModemPresetSettings()
                    initializeDefaultSlot()
                    WizardStep.FREQUENCY_SLOT
                }
                WizardStep.FREQUENCY_SLOT -> {
                    applySlotToFrequency()
                    WizardStep.REVIEW_CONFIGURE
                }
                WizardStep.REVIEW_CONFIGURE -> WizardStep.REVIEW_CONFIGURE
            }
        _state.update { it.copy(currentStep = nextStep) }
    }

    fun goToPreviousStep() {
        val currentState = _state.value
        val prevStep =
            when (currentState.currentStep) {
                WizardStep.DEVICE_DISCOVERY -> WizardStep.DEVICE_DISCOVERY
                WizardStep.REGION_SELECTION -> WizardStep.DEVICE_DISCOVERY
                WizardStep.MODEM_PRESET -> WizardStep.REGION_SELECTION
                WizardStep.FREQUENCY_SLOT -> WizardStep.MODEM_PRESET
                WizardStep.REVIEW_CONFIGURE ->
                    if (currentState.isCustomMode || currentState.selectedPreset != null) {
                        WizardStep.REGION_SELECTION
                    } else {
                        WizardStep.FREQUENCY_SLOT
                    }
            }
        _state.update { it.copy(currentStep = prevStep) }
    }

    fun canProceed(): Boolean {
        val state = _state.value
        return when (state.currentStep) {
            WizardStep.DEVICE_DISCOVERY ->
                when (state.connectionType) {
                    RNodeConnectionType.TCP_WIFI ->
                        state.tcpHost.isNotBlank()
                    RNodeConnectionType.BLUETOOTH ->
                        state.selectedDevice != null ||
                            (
                                state.showManualEntry &&
                                    state.manualDeviceName.isNotBlank() &&
                                    state.manualDeviceNameError == null
                            )
                    RNodeConnectionType.USB_SERIAL ->
                        state.selectedUsbDevice != null && state.selectedUsbDevice.hasPermission
                }
            WizardStep.REGION_SELECTION ->
                state.selectedFrequencyRegion != null || state.isCustomMode || state.selectedPreset != null
            WizardStep.MODEM_PRESET ->
                true
            WizardStep.FREQUENCY_SLOT ->
                true
            WizardStep.REVIEW_CONFIGURE ->
                state.interfaceName.isNotBlank() && validateConfigurationSilent()
        }
    }

    private fun applyFrequencyRegionSettings() {
        val region = _state.value.selectedFrequencyRegion ?: return

        val airtimeLimit =
            if (region.dutyCycle < 100) {
                region.dutyCycle.toDouble().toString()
            } else {
                ""
            }

        _state.update {
            it.copy(
                frequency = if ("frequency" !in userModifiedFields) region.frequency.toString() else it.frequency,
                frequencyError = if ("frequency" !in userModifiedFields) null else it.frequencyError,
                txPower = if ("txPower" !in userModifiedFields) region.defaultTxPower.toString() else it.txPower,
                txPowerError = if ("txPower" !in userModifiedFields) null else it.txPowerError,
                stAlock = if ("stAlock" !in userModifiedFields) airtimeLimit else it.stAlock,
                stAlockError = if ("stAlock" !in userModifiedFields) null else it.stAlockError,
                ltAlock = if ("ltAlock" !in userModifiedFields) airtimeLimit else it.ltAlock,
                ltAlockError = if ("ltAlock" !in userModifiedFields) null else it.ltAlockError,
            )
        }
    }

    private fun applyModemPresetSettings() {
        val preset = _state.value.selectedModemPreset
        _state.update {
            it.copy(
                bandwidth = preset.bandwidth.toString(),
                spreadingFactor = preset.spreadingFactor.toString(),
                codingRate = preset.codingRate.toString(),
            )
        }
    }

    private fun initializeDefaultSlot() {
        val region = _state.value.selectedFrequencyRegion ?: return
        val bandwidth = _state.value.selectedModemPreset.bandwidth
        val defaultSlot = FrequencySlotCalculator.getDefaultSlot(region, bandwidth)
        _state.update { it.copy(selectedSlot = defaultSlot) }
    }

    private fun applySlotToFrequency() {
        val state = _state.value
        val region = state.selectedFrequencyRegion ?: return

        val frequency =
            state.customFrequency ?: run {
                val bandwidth = state.selectedModemPreset.bandwidth
                FrequencySlotCalculator.calculateFrequency(region, bandwidth, state.selectedSlot)
            }
        _state.update { it.copy(frequency = frequency.toString(), frequencyError = null) }
    }

    // ========== STEP 4: FREQUENCY SLOT SELECTION ==========

    fun getNumSlots(): Int {
        val region = _state.value.selectedFrequencyRegion ?: return 0
        val bandwidth = _state.value.selectedModemPreset.bandwidth
        return FrequencySlotCalculator.getNumSlots(region, bandwidth)
    }

    fun getFrequencyForSlot(slot: Int): Long {
        val region = _state.value.selectedFrequencyRegion ?: return 0
        val bandwidth = _state.value.selectedModemPreset.bandwidth
        return FrequencySlotCalculator.calculateFrequency(region, bandwidth, slot)
    }

    fun getCommunitySlots(): List<tech.torlando.rns.rnode.data.CommunitySlot> {
        val region = _state.value.selectedFrequencyRegion ?: return emptyList()
        return CommunitySlots.forRegion(region.id)
    }

    fun selectSlot(slot: Int) {
        val numSlots = getNumSlots()
        if (slot in 0 until numSlots) {
            _state.update {
                it.copy(
                    selectedSlot = slot,
                    customFrequency = null,
                    selectedSlotPreset = null,
                )
            }
        }
    }

    fun selectPresetFrequency(preset: RNodeRegionalPreset) {
        _state.update {
            it.copy(
                customFrequency = preset.frequency,
                selectedSlotPreset = preset,
            )
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun getPopularPresetsForRegion(): List<RNodeRegionalPreset> {
        val region = _state.value.selectedFrequencyRegion ?: return emptyList()

        if (region.id == "eu_433") {
            return RNodeRegionalPresets.presets
                .filter { it.frequency in 430_000_000..440_000_000 }
                .take(5)
        }

        if (region.id == "lora_24") {
            return RNodeRegionalPresets.presets
                .filter { it.frequency in 2_400_000_000..2_500_000_000 }
                .take(5)
        }

        when (region.id) {
            "eu_868_l" -> {
                return RNodeRegionalPresets.presets
                    .filter { it.frequency in 865_000_000..867_999_999 }
                    .take(5)
            }
            "eu_868_m" -> {
                return RNodeRegionalPresets.presets
                    .filter { it.frequency in 868_000_000..868_599_999 }
                    .take(5)
            }
            "eu_868_p" -> {
                return RNodeRegionalPresets.presets
                    .filter { it.frequency in 869_400_000..869_650_000 }
                    .take(5)
            }
            "eu_868_q" -> {
                return RNodeRegionalPresets.presets
                    .filter { it.frequency in 869_700_000..869_999_999 }
                    .take(5)
            }
        }

        val countryCodes =
            when (region.id) {
                "us_915" -> listOf("US")
                "br_902" -> return emptyList()
                "ru_868" -> return emptyList()
                "ua_868" -> return emptyList()
                "au_915" -> listOf("AU")
                "nz_865" -> emptyList()
                "jp_920" -> return emptyList()
                "kr_920", "tw_920", "th_920", "sg_923", "my_919" ->
                    listOf("MY", "SG", "TH")
                "ph_915" -> emptyList()
                else -> emptyList()
            }

        return RNodeRegionalPresets.presets
            .filter { it.countryCode in countryCodes }
            .filter { it.frequency !in 430_000_000..440_000_000 }
            .filter { it.frequency !in 2_400_000_000..2_500_000_000 }
            .take(5)
    }

    // ========== STEP 1: DEVICE DISCOVERY ==========

    @SuppressLint("MissingPermission")
    fun startDeviceScan() {
        if (bluetoothAdapter == null) {
            setScanError("Bluetooth not available on this device")
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isScanning = true,
                    scanError = null,
                    discoveredDevices = emptyList(),
                )
            }

            val bleDeviceAddresses = mutableSetOf<String>()
            val devices = mutableMapOf<String, DiscoveredRNode>()

            performBleScan(bleDeviceAddresses, devices)
            addBondedRNodes(bleDeviceAddresses, devices)

            val updatedSelected = updateSelectedFromScan(devices.values)

            finalizeScan(devices.values.toList(), updatedSelected)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun performBleScan(
        bleDeviceAddresses: MutableSet<String>,
        devices: MutableMap<String, DiscoveredRNode>,
    ) {
        try {
            scanForBleRNodes { bleDevice ->
                bleDeviceAddresses.add(bleDevice.address)
                devices[bleDevice.address] = bleDevice
                deviceClassifier.cacheDeviceType(bleDevice.address, BluetoothType.BLE)
                _state.update { it.copy(discoveredDevices = devices.values.toList()) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "BLE scan failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun addBondedRNodes(
        bleDeviceAddresses: Set<String>,
        devices: MutableMap<String, DiscoveredRNode>,
    ) {
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                if (deviceClassifier.shouldIncludeInDiscovery(device)) {
                    classifyBondedDevice(device, bleDeviceAddresses, devices)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission for bonded devices check", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun classifyBondedDevice(
        device: BluetoothDevice,
        bleDeviceAddresses: Set<String>,
        devices: MutableMap<String, DiscoveredRNode>,
    ) {
        val address = device.address
        val name = device.name ?: address

        when (val result = deviceClassifier.classifyDevice(device, bleDeviceAddresses)) {
            is DeviceClassifier.ClassificationResult.ConfirmedBle -> {
                devices[address]?.let { existing ->
                    devices[address] = existing.copy(isPaired = true)
                }
            }
            is DeviceClassifier.ClassificationResult.Cached -> {
                devices[address] =
                    DiscoveredRNode(
                        name = name,
                        address = address,
                        type = result.type,
                        rssi = null,
                        isPaired = true,
                        bluetoothDevice = device,
                    )
                Log.d(TAG, "Using cached type for $name: ${result.type}")
            }
            is DeviceClassifier.ClassificationResult.Unknown -> {
                devices[address] =
                    DiscoveredRNode(
                        name = name,
                        address = address,
                        type = BluetoothType.UNKNOWN,
                        rssi = null,
                        isPaired = true,
                        bluetoothDevice = device,
                    )
                Log.d(TAG, "Unknown type for bonded device $name (no cache)")
            }
        }
    }

    private fun updateSelectedFromScan(devices: Collection<DiscoveredRNode>): DiscoveredRNode? {
        val currentSelected = _state.value.selectedDevice ?: return null
        return devices.find { it.name == currentSelected.name }?.also { foundDevice ->
            Log.d(TAG, "Updating selected device from scan: rssi=${foundDevice.rssi}")
        } ?: currentSelected
    }

    private fun finalizeScan(
        devices: List<DiscoveredRNode>,
        selectedDevice: DiscoveredRNode?,
    ) {
        _state.update {
            it.copy(
                discoveredDevices = devices,
                isScanning = false,
                selectedDevice = selectedDevice,
            )
        }

        if (devices.isEmpty()) {
            _state.update {
                it.copy(
                    scanError =
                        "No RNode devices found. " +
                            "Make sure your RNode is powered on and Bluetooth is enabled.",
                )
            }
        }
    }

    fun setDeviceType(
        device: DiscoveredRNode,
        type: BluetoothType,
    ) {
        deviceClassifier.cacheDeviceType(device.address, type)
        val updatedDevice = device.copy(type = type)
        _state.update { state ->
            val isSelected = state.selectedDevice?.address == device.address
            val newSelected = if (isSelected) updatedDevice else state.selectedDevice
            state.copy(
                discoveredDevices =
                    state.discoveredDevices.map {
                        if (it.address == device.address) updatedDevice else it
                    },
                selectedDevice = newSelected,
                interfaceName = if (isSelected) defaultInterfaceNameFor(state, updatedDevice) else state.interfaceName,
            )
        }
    }

    /**
     * Returns the appropriate interface name for a device.
     * If user hasn't customized the name (still default), generates "RNode <identifier> <BLE|BT>".
     * Otherwise preserves the user's custom name.
     */
    private fun defaultInterfaceNameFor(state: RNodeWizardState, device: DiscoveredRNode): String =
        if (state.interfaceName == DEFAULT_INTERFACE_NAME) {
            val identifier =
                device.name
                    .removePrefix("RNode ")
                    .trim()
                    .ifEmpty { device.name }
            val suffix = if (device.type == BluetoothType.BLE) "BLE" else "BT"
            "RNode $identifier $suffix"
        } else {
            state.interfaceName
        }

    @SuppressLint("MissingPermission")
    private suspend fun scanForBleRNodes(onDeviceFound: (DiscoveredRNode) -> Unit) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val filter =
            ScanFilter
                .Builder()
                .setServiceUuid(ParcelUuid(NUS_SERVICE_UUID))
                .build()

        val settings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        val foundDevices = mutableSetOf<String>()

        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val name = result.device.name ?: return
                    if (!name.startsWith("RNode ")) return

                    val address = result.device.address
                    if (foundDevices.contains(address)) {
                        updateDeviceRssi(address, result.rssi)
                    } else {
                        foundDevices.add(address)
                        onDeviceFound(
                            DiscoveredRNode(
                                name = name,
                                address = address,
                                type = BluetoothType.BLE,
                                rssi = result.rssi,
                                isPaired = result.device.bondState == BluetoothDevice.BOND_BONDED,
                                bluetoothDevice = result.device,
                            ),
                        )
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "BLE scan failed: $errorCode")
                    val errorMessage =
                        when (errorCode) {
                            1 -> "BLE scan failed: already started"
                            2 -> "BLE scan failed: app not registered"
                            3 -> "BLE scan failed: internal error"
                            4 -> "BLE scan failed: feature unsupported"
                            5 -> "BLE scan failed: out of hardware resources"
                            6 -> "BLE scan failed: scanning too frequently"
                            else -> "BLE scan failed: error code $errorCode"
                        }
                    setScanError(errorMessage)
                }
            }

        try {
            scanner.startScan(listOf(filter), settings, callback)
            delay(SCAN_DURATION_MS)
            scanner.stopScan(callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan permission denied", e)
            setScanError("Bluetooth permission required. Please grant Bluetooth permissions in Settings.")
        }
    }

    private fun setScanError(message: String) {
        _state.update {
            it.copy(
                scanError = message,
                isScanning = false,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanForDeviceByName(
        deviceName: String,
        deviceType: BluetoothType,
    ): DiscoveredRNode? {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return null

        val filter =
            ScanFilter
                .Builder()
                .setServiceUuid(ParcelUuid(NUS_SERVICE_UUID))
                .build()

        val settings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        var foundDevice: DiscoveredRNode? = null

        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val name = result.device.name ?: return
                    if (name == deviceName) {
                        foundDevice =
                            DiscoveredRNode(
                                name = name,
                                address = result.device.address,
                                type = deviceType,
                                rssi = result.rssi,
                                isPaired = result.device.bondState == BluetoothDevice.BOND_BONDED,
                                bluetoothDevice = result.device,
                            )
                        Log.d(TAG, "Found device during reconnect scan: $name")
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Reconnect scan failed: $errorCode")
                }
            }

        try {
            scanner.startScan(listOf(filter), settings, callback)

            val startTime = System.currentTimeMillis()
            while (foundDevice == null &&
                System.currentTimeMillis() - startTime < RECONNECT_SCAN_TIMEOUT_MS
            ) {
                if (!_state.value.isWaitingForReconnect) {
                    Log.d(TAG, "Reconnect scan cancelled by user")
                    break
                }
                delay(200)
            }

            scanner.stopScan(callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Reconnect scan permission denied", e)
        }

        return foundDevice
    }

    fun cancelReconnectScan() {
        Log.d(TAG, "User cancelled reconnect scan")
        _state.update {
            it.copy(
                isWaitingForReconnect = false,
                reconnectDeviceName = null,
                isPairingInProgress = false,
            )
        }
    }

    fun selectDevice(device: DiscoveredRNode) {
        _state.update {
            it.copy(
                selectedDevice = device,
                showManualEntry = false,
                interfaceName = defaultInterfaceNameFor(it, device),
            )
        }
    }

    private fun updateDeviceRssi(
        address: String,
        rssi: Int,
    ) {
        if (!rssiThrottler.shouldUpdate(address)) return

        _state.update { state ->
            val updatedDevices =
                state.discoveredDevices.map { device ->
                    if (device.address == address) device.copy(rssi = rssi) else device
                }
            val updatedSelected =
                state.selectedDevice?.let { selected ->
                    if (selected.address == address) selected.copy(rssi = rssi) else selected
                }
            state.copy(
                discoveredDevices = updatedDevices,
                selectedDevice = updatedSelected,
            )
        }
    }

    // ========== COMPANION DEVICE ASSOCIATION (Android 12+) ==========

    private val companionDeviceManager: CompanionDeviceManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
        } else {
            null
        }

    fun isCompanionDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && companionDeviceManager != null

    @SuppressLint("MissingPermission")
    fun requestDeviceAssociation(
        device: DiscoveredRNode,
        onFallback: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || companionDeviceManager == null) {
            onFallback()
            return
        }

        if (device.bluetoothDevice != null) {
            Log.d(TAG, "Device already discovered, skipping CDM: ${device.name}")
            selectDevice(device)
            return
        }

        Log.d(TAG, "Requesting CDM association for ${device.name} (${device.type})")
        _state.update { it.copy(isAssociating = true, associationError = null) }

        try {
            val request = buildAssociationRequest(device)

            companionDeviceManager.associate(
                request,
                object : CompanionDeviceManager.Callback() {
                    override fun onAssociationPending(intentSender: IntentSender) {
                        Log.d(TAG, "Association pending - providing IntentSender to UI")
                        _state.update {
                            it.copy(
                                pendingAssociationIntent = intentSender,
                                isAssociating = true,
                            )
                        }
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onDeviceFound(intentSender: IntentSender) {
                        Log.d(TAG, "Device found (legacy) - providing IntentSender to UI")
                        _state.update {
                            it.copy(
                                pendingAssociationIntent = intentSender,
                                isAssociating = true,
                            )
                        }
                    }

                    override fun onAssociationCreated(associationInfo: AssociationInfo) {
                        Log.d(TAG, "Association created: ${associationInfo.id}")

                        try {
                            companionDeviceManager?.startObservingDevicePresence(
                                associationInfo.deviceMacAddress?.toString()
                                    ?: device.address,
                            )
                            Log.d(TAG, "Started observing device presence")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to start observing device presence", e)
                        }

                        _state.update {
                            it.copy(
                                selectedDevice = device,
                                isAssociating = false,
                                pendingAssociationIntent = null,
                                showManualEntry = false,
                                interfaceName = defaultInterfaceNameFor(it, device),
                            )
                        }
                        deviceClassifier.cacheDeviceType(device.address, device.type)
                    }

                    override fun onFailure(error: CharSequence?) {
                        Log.e(TAG, "CDM association failed: $error")
                        _state.update {
                            it.copy(
                                isAssociating = false,
                                pendingAssociationIntent = null,
                                associationError = error?.toString() ?: "Association failed",
                            )
                        }
                    }
                },
                null,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start CDM association", e)
            _state.update {
                it.copy(
                    isAssociating = false,
                    associationError = e.message ?: "Failed to start association",
                )
            }
        }
    }

    @SuppressLint("NewApi")
    private fun buildAssociationRequest(device: DiscoveredRNode): AssociationRequest {
        val builder = AssociationRequest.Builder()
        val escapedName = Pattern.quote(device.name)

        if (device.type == BluetoothType.BLE) {
            val bleFilter =
                BluetoothLeDeviceFilter
                    .Builder()
                    .setNamePattern(Pattern.compile(escapedName))
                    .setScanFilter(
                        ScanFilter
                            .Builder()
                            .setServiceUuid(ParcelUuid(NUS_SERVICE_UUID))
                            .build(),
                    ).build()
            builder.addDeviceFilter(bleFilter)
        } else {
            val classicFilter =
                BluetoothDeviceFilter
                    .Builder()
                    .setNamePattern(Pattern.compile(escapedName))
                    .build()
            builder.addDeviceFilter(classicFilter)
        }

        builder.setSingleDevice(true)

        return builder.build()
    }

    fun onAssociationIntentLaunched() {
        _state.update { it.copy(pendingAssociationIntent = null) }
    }

    fun onAssociationCancelled() {
        Log.d(TAG, "User cancelled association")
        _state.update {
            it.copy(
                isAssociating = false,
                pendingAssociationIntent = null,
            )
        }
    }

    fun clearAssociationError() {
        _state.update { it.copy(associationError = null) }
    }

    fun showManualEntry() {
        _state.update {
            it.copy(
                showManualEntry = true,
                selectedDevice = null,
            )
        }
    }

    fun hideManualEntry() {
        _state.update {
            it.copy(
                showManualEntry = false,
            )
        }
    }

    fun updateManualDeviceName(name: String) {
        val (error, warning) = validateManualDeviceName(name)
        _state.update {
            it.copy(
                manualDeviceName = name,
                manualDeviceNameError = error,
                manualDeviceNameWarning = warning,
            )
        }
    }

    private fun validateManualDeviceName(name: String): Pair<String?, String?> =
        when (val result = DeviceNameValidator.validate(name)) {
            is DeviceNameValidator.ValidationResult.Valid -> null to null
            is DeviceNameValidator.ValidationResult.Error -> result.message to null
            is DeviceNameValidator.ValidationResult.Warning -> null to result.message
        }

    fun updateManualBluetoothType(type: BluetoothType) {
        _state.update { it.copy(manualBluetoothType = type) }
    }

    // ========== TCP/WiFi Connection Methods ==========

    fun setConnectionType(type: RNodeConnectionType) {
        _state.update {
            val isAutoGeneratedName =
                it.interfaceName == DEFAULT_INTERFACE_NAME ||
                    it.interfaceName == "RNode TCP" ||
                    it.interfaceName == "RNode USB" ||
                    it.interfaceName.matches(Regex("RNode .+ (BLE|BT)"))

            val newInterfaceName =
                if (isAutoGeneratedName) {
                    when (type) {
                        RNodeConnectionType.TCP_WIFI -> "RNode TCP"
                        RNodeConnectionType.USB_SERIAL -> "RNode USB"
                        RNodeConnectionType.BLUETOOTH -> DEFAULT_INTERFACE_NAME
                    }
                } else {
                    it.interfaceName
                }

            it.copy(
                connectionType = type,
                interfaceName = newInterfaceName,
                selectedDevice = if (type != RNodeConnectionType.BLUETOOTH) null else it.selectedDevice,
                tcpValidationSuccess = if (type != RNodeConnectionType.TCP_WIFI) null else it.tcpValidationSuccess,
                tcpValidationError = if (type != RNodeConnectionType.TCP_WIFI) null else it.tcpValidationError,
                selectedUsbDevice = if (type != RNodeConnectionType.USB_SERIAL) null else it.selectedUsbDevice,
                usbScanError = if (type != RNodeConnectionType.USB_SERIAL) null else it.usbScanError,
                usbBluetoothPin = if (type != RNodeConnectionType.USB_SERIAL) null else it.usbBluetoothPin,
                isUsbPairingMode = if (type != RNodeConnectionType.USB_SERIAL) false else it.isUsbPairingMode,
            )
        }

        if (type == RNodeConnectionType.USB_SERIAL) {
            scanUsbDevices()
        }
    }

    fun updateTcpHost(host: String) {
        _state.update {
            it.copy(
                tcpHost = host,
                tcpValidationSuccess = null,
                tcpValidationError = null,
            )
        }
    }

    fun updateTcpPort(port: String) {
        _state.update {
            it.copy(
                tcpPort = port,
                tcpValidationSuccess = null,
                tcpValidationError = null,
            )
        }
    }

    fun validateTcpConnection() {
        val state = _state.value
        val host = state.tcpHost.trim()
        val port = state.tcpPort.toIntOrNull() ?: 7633

        if (host.isBlank()) {
            _state.update {
                it.copy(
                    tcpValidationSuccess = false,
                    tcpValidationError = "Host cannot be empty",
                )
            }
            return
        }

        if (port !in 1..65535) {
            _state.update {
                it.copy(
                    tcpValidationSuccess = false,
                    tcpValidationError = "Port must be between 1 and 65535",
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isTcpValidating = true, tcpValidationError = null) }

            try {
                val success =
                    withContext(Dispatchers.IO) {
                        try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(host, port), TCP_CONNECTION_TIMEOUT_MS)
                                true
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "TCP connection test failed: ${e.message}")
                            false
                        }
                    }

                _state.update {
                    it.copy(
                        isTcpValidating = false,
                        tcpValidationSuccess = success,
                        tcpValidationError = if (!success) "Could not connect to $host:$port" else null,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP validation error", e)
                _state.update {
                    it.copy(
                        isTcpValidating = false,
                        tcpValidationSuccess = false,
                        tcpValidationError = e.message ?: "Connection failed",
                    )
                }
            }
        }
    }

    // =========================================================================
    // USB Serial Methods
    // =========================================================================

    fun scanUsbDevices() {
        viewModelScope.launch {
            _state.update { it.copy(isUsbScanning = true, usbScanError = null) }

            try {
                val devices =
                    withContext(Dispatchers.IO) {
                        usbBridge.getConnectedUsbDevices()
                    }

                val usbDevices =
                    devices.map { device ->
                        DiscoveredUsbDevice(
                            deviceId = device.deviceId,
                            vendorId = device.vendorId,
                            productId = device.productId,
                            deviceName = device.deviceName,
                            manufacturerName = device.manufacturerName,
                            productName = device.productName,
                            serialNumber = device.serialNumber,
                            driverType = device.driverType,
                            hasPermission = usbBridge.hasPermission(device.deviceId),
                        )
                    }

                _state.update {
                    it.copy(
                        isUsbScanning = false,
                        usbDevices = usbDevices,
                        usbScanError = if (usbDevices.isEmpty()) "No USB serial devices found" else null,
                    )
                }

                Log.d(TAG, "Found ${usbDevices.size} USB serial device(s)")
            } catch (e: Exception) {
                Log.e(TAG, "USB scan failed", e)
                _state.update {
                    it.copy(
                        isUsbScanning = false,
                        usbScanError = "Failed to scan USB devices: ${e.message}",
                    )
                }
            }
        }
    }

    fun selectUsbDevice(device: DiscoveredUsbDevice) {
        if (device.hasPermission) {
            _state.update {
                val newInterfaceName =
                    if (it.interfaceName == DEFAULT_INTERFACE_NAME) {
                        "RNode USB"
                    } else {
                        it.interfaceName
                    }
                it.copy(
                    selectedUsbDevice = device,
                    interfaceName = newInterfaceName,
                )
            }
        } else {
            requestUsbPermission(device)
        }
    }

    fun requestUsbPermission(device: DiscoveredUsbDevice) {
        _state.update { it.copy(isRequestingUsbPermission = true) }

        usbBridge.requestPermission(device.deviceId) { granted ->
            viewModelScope.launch {
                if (granted) {
                    Log.d(TAG, "USB permission granted for device ${device.deviceId}")
                    val updatedDevices =
                        _state.value.usbDevices.map {
                            if (it.deviceId == device.deviceId) it.copy(hasPermission = true) else it
                        }
                    val updatedDevice = device.copy(hasPermission = true)

                    _state.update {
                        val newInterfaceName =
                            if (it.interfaceName == DEFAULT_INTERFACE_NAME) {
                                "RNode USB"
                            } else {
                                it.interfaceName
                            }
                        it.copy(
                            isRequestingUsbPermission = false,
                            usbDevices = updatedDevices,
                            selectedUsbDevice = updatedDevice,
                            interfaceName = newInterfaceName,
                        )
                    }
                } else {
                    Log.w(TAG, "USB permission denied for device ${device.deviceId}")
                    _state.update {
                        it.copy(
                            isRequestingUsbPermission = false,
                            usbScanError = "USB permission denied. Please grant permission to use this device.",
                        )
                    }
                }
            }
        }
    }

    fun preselectUsbDevice(
        deviceId: Int,
        vendorId: Int,
        productId: Int,
        deviceName: String,
    ) {
        Log.d(TAG, "Pre-selecting USB device: $deviceId ($deviceName)")

        setConnectionType(RNodeConnectionType.USB_SERIAL)
        scanUsbDevices()

        viewModelScope.launch {
            delay(500)

            val scannedDevice = _state.value.usbDevices.find { it.deviceId == deviceId }
            if (scannedDevice != null) {
                selectUsbDevice(scannedDevice)
                Log.d(TAG, "Found and selected USB device: ${scannedDevice.deviceId}")
            } else {
                val placeholderDevice =
                    DiscoveredUsbDevice(
                        deviceId = deviceId,
                        vendorId = vendorId,
                        productId = productId,
                        deviceName = deviceName,
                        manufacturerName = null,
                        productName = "USB RNode",
                        serialNumber = null,
                        driverType = "Unknown",
                        hasPermission = false,
                    )
                _state.update {
                    it.copy(
                        usbDevices = listOf(placeholderDevice) + it.usbDevices,
                        selectedUsbDevice = null,
                    )
                }
                requestUsbPermission(placeholderDevice)
                Log.d(TAG, "Created placeholder USB device and requesting permission")
            }
        }
    }

    @Suppress("LongMethod")
    fun enterUsbBluetoothPairingMode() {
        val device = _state.value.selectedUsbDevice ?: return

        viewModelScope.launch {
            _state.update { it.copy(isUsbPairingMode = true, usbBluetoothPin = null) }

            try {
                var pinReceived = false

                usbBridge.setOnBluetoothPinReceivedKotlin { pin ->
                    viewModelScope.launch {
                        Log.d(TAG, "Received Bluetooth PIN from RNode: $pin")
                        pinReceived = true
                        _state.update { it.copy(usbBluetoothPin = pin) }

                        initiateAutoPairingWithPin(pin)
                    }
                }

                val connected =
                    withContext(Dispatchers.IO) {
                        usbBridge.connect(device.deviceId)
                    }

                if (!connected) {
                    _state.update {
                        it.copy(
                            isUsbPairingMode = false,
                            usbScanError = "Failed to connect to USB device",
                        )
                    }
                    return@launch
                }

                val kissPairingCmd =
                    byteArrayOf(
                        0xC0.toByte(),
                        0x46.toByte(),
                        0x02.toByte(),
                        0xC0.toByte(),
                    )

                val written =
                    withContext(Dispatchers.IO) {
                        usbBridge.write(kissPairingCmd)
                    }

                if (written != kissPairingCmd.size) {
                    _state.update {
                        it.copy(
                            isUsbPairingMode = false,
                            usbScanError = "Failed to send pairing command",
                        )
                    }
                    usbBridge.disconnect()
                    return@launch
                }

                Log.d(TAG, "Bluetooth pairing mode command sent via USB")

                _state.update {
                    it.copy(usbPairingStatus = "Waiting for PIN or scanning for RNode...")
                }

                Log.d(TAG, "Starting BOTH Classic BT and BLE discovery immediately after pairing command")
                startClassicBluetoothDiscoveryForPairing()
                startBleScanForPairingEarly()

                try {
                    withTimeout(3_000L) {
                        while (!pinReceived) {
                            delay(100)
                        }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.w(TAG, "Timeout waiting for Bluetooth PIN from RNode - prompting for manual entry", e)
                    _state.update {
                        it.copy(
                            showManualPinEntry = true,
                            usbPairingStatus = "Enter the PIN shown on your RNode's display",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enter Bluetooth pairing mode", e)
                _state.update {
                    it.copy(
                        isUsbPairingMode = false,
                        usbScanError = "Error: ${e.message}",
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun initiateAutoPairingWithPin(pin: String) {
        Log.d(TAG, "Initiating auto-pairing with PIN: $pin")

        pairingHandler?.unregister()

        val handler =
            BlePairingHandler(context).apply {
                setAutoPairPin(pin)
                register()
            }
        pairingHandler = handler

        try {
            Log.d(TAG, "Starting BOTH Classic BT discovery AND BLE scan for RNode")
            _state.update { it.copy(usbPairingStatus = "Scanning for RNode...") }

            startClassicBluetoothDiscovery(pin)
            startBleScanForPairing(pin)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-pairing failed", e)
            _state.update { it.copy(usbPairingStatus = "Auto-pairing failed: ${e.message}") }
        }
    }

    private var pairingScanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    private fun stopAllPairingScans() {
        pairingScanCallback?.let {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop pairing BLE scan", e)
            }
            pairingScanCallback = null
        }
        earlyBleScanCallback?.let {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop early BLE scan", e)
            }
            earlyBleScanCallback = null
        }
        bluetoothAdapter?.cancelDiscovery()
        classicDiscoveryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister discovery receiver", e)
            }
            classicDiscoveryReceiver = null
        }
        bondReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister bond receiver", e)
            }
            bondReceiver = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScanForPairing(pin: String) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            _state.update { it.copy(usbPairingStatus = "BLE scanner not available") }
            return
        }

        val bondedAddresses =
            bluetoothAdapter?.bondedDevices?.map { it.address }?.toSet() ?: emptySet()
        Log.d(TAG, "Bonded device addresses to skip: $bondedAddresses")

        val scanSettings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val device = result.device
                    val deviceName = device.name ?: return

                    if (!deviceName.startsWith("RNode", ignoreCase = true)) return

                    val isAlreadyBonded = bondedAddresses.contains(device.address)

                    Log.d(
                        TAG,
                        "BLE scan found: $deviceName (${device.address}), " +
                            "bondState=${device.bondState}, isAlreadyBonded=$isAlreadyBonded",
                    )

                    try {
                        scanner.stopScan(this)
                        pairingScanCallback = null
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to stop BLE scan", e)
                    }

                    if (isAlreadyBonded) {
                        Log.i(TAG, "Found already-bonded RNode via BLE: $deviceName - adding to discovered devices")
                        val discoveredNode =
                            DiscoveredRNode(
                                name = deviceName,
                                address = device.address,
                                type = BluetoothType.BLE,
                                rssi = result.rssi,
                                isPaired = true,
                                bluetoothDevice = device,
                            )
                        viewModelScope.launch {
                            _state.update { state ->
                                state.copy(
                                    usbPairingStatus = "$deviceName is already paired!",
                                    isUsbPairingMode = false,
                                    usbBluetoothPin = null,
                                    discoveredDevices =
                                        state.discoveredDevices.map {
                                            if (it.address == device.address) discoveredNode else it
                                        } +
                                            if (state.discoveredDevices.none { it.address == device.address }) {
                                                listOf(discoveredNode)
                                            } else {
                                                emptyList()
                                            },
                                    selectedDevice = discoveredNode,
                                    interfaceName = defaultInterfaceNameFor(state, discoveredNode),
                                )
                            }
                        }
                    } else {
                        Log.i(TAG, "Discovered unbonded RNode via BLE: $deviceName")

                        pairingHandler?.setAutoPairPin(pin, device.address)

                        viewModelScope.launch {
                            _state.update {
                                it.copy(usbPairingStatus = "Found $deviceName, pairing...")
                            }

                            try {
                                val createBondMethod =
                                    BluetoothDevice::class.java.getMethod(
                                        "createBond",
                                        Int::class.javaPrimitiveType,
                                    )
                                createBondMethod.invoke(device, 2)
                                Log.d(TAG, "createBond(TRANSPORT_LE) called for RNode")
                            } catch (e: Exception) {
                                Log.w(TAG, "createBond with transport failed, using default", e)
                                device.createBond()
                            }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "BLE pairing scan failed: $errorCode")
                    pairingScanCallback = null
                    val errorMessage =
                        when (errorCode) {
                            1 -> "BLE scan failed: already started"
                            2 -> "BLE scan failed: app not registered"
                            3 -> "BLE scan failed: internal error"
                            4 -> "BLE scan failed: feature unsupported"
                            5 -> "BLE scan failed: out of hardware resources"
                            6 -> "BLE scan failed: scanning too frequently"
                            else -> "BLE scan failed: error code $errorCode"
                        }
                    _state.update { it.copy(usbPairingStatus = errorMessage) }
                }
            }

        pairingScanCallback = callback
        Log.d(TAG, "Starting BLE scan for RNode pairing (no UUID filter)")

        try {
            scanner.startScan(null, scanSettings, callback)

            viewModelScope.launch {
                delay(30_000)
                pairingScanCallback?.let { cb ->
                    Log.d(TAG, "BLE pairing scan timeout - stopping scan")
                    try {
                        scanner.stopScan(cb)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to stop BLE scan on timeout", e)
                    }
                    pairingScanCallback = null
                    if (_state.value.usbPairingStatus == "Scanning for RNode...") {
                        _state.update {
                            it.copy(usbPairingStatus = "No RNode found. Make sure your RNode is in pairing mode.")
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan permission denied", e)
            _state.update { it.copy(usbPairingStatus = "Bluetooth permission required") }
        }
    }

    private var classicDiscoveryReceiver: BroadcastReceiver? = null
    private var bondReceiver: BroadcastReceiver? = null
    private var discoveredPairingDevice: BluetoothDevice? = null
    private var discoveredPairingTransport: Int? = null
    private var discoveredPairingRssi: Int? = null

    @SuppressLint("MissingPermission")
    private fun startClassicBluetoothDiscovery(pin: String) {
        val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
        val bondedAddresses = bondedDevices.map { it.address }.toSet()
        Log.d(TAG, "Starting Classic BT discovery. Bonded addresses to skip: $bondedAddresses")

        classicDiscoveryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister previous discovery receiver", e)
            }
        }

        val discoveryReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(
                                        BluetoothDevice.EXTRA_DEVICE,
                                        BluetoothDevice::class.java,
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }

                            device?.let {
                                val deviceName = it.name ?: return
                                val isAlreadyBonded = bondedAddresses.contains(it.address)

                                Log.d(
                                    TAG,
                                    "Classic BT discovery found: $deviceName (${it.address}), " +
                                        "bondState=${it.bondState}, isAlreadyBonded=$isAlreadyBonded",
                                )

                                if (deviceName.startsWith("RNode", ignoreCase = true) && !isAlreadyBonded) {
                                    Log.i(TAG, "Discovered unbonded RNode via Classic BT: $deviceName")
                                    bluetoothAdapter?.cancelDiscovery()

                                    pairingHandler?.setAutoPairPin(pin, it.address)

                                    viewModelScope.launch {
                                        _state.update { state ->
                                            state.copy(usbPairingStatus = "Found $deviceName, pairing...")
                                        }

                                        try {
                                            val createBondMethod =
                                                BluetoothDevice::class.java.getMethod(
                                                    "createBond",
                                                    Int::class.javaPrimitiveType,
                                                )
                                            createBondMethod.invoke(it, 1)
                                            Log.d(TAG, "createBond(TRANSPORT_BREDR) called for RNode")
                                        } catch (e: Exception) {
                                            Log.w(TAG, "createBond with transport failed, using default", e)
                                            it.createBond()
                                        }
                                    }

                                    try {
                                        context.unregisterReceiver(this)
                                        classicDiscoveryReceiver = null
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to unregister discovery receiver", e)
                                    }
                                }
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.d(TAG, "Classic Bluetooth discovery finished")
                            if (_state.value.usbPairingStatus == "Scanning for RNode...") {
                                _state.update {
                                    it.copy(usbPairingStatus = "No RNode found. Make sure your RNode is in pairing mode.")
                                }
                            }
                            try {
                                context.unregisterReceiver(this)
                                classicDiscoveryReceiver = null
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to unregister discovery receiver", e)
                            }
                        }
                    }
                }
            }

        classicDiscoveryReceiver = discoveryReceiver

        val filter =
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(discoveryReceiver, filter)
        }

        Log.d(TAG, "Starting Classic Bluetooth discovery for RNode")
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun startClassicBluetoothDiscoveryForPairing() {
        discoveredPairingDevice = null
        discoveredPairingTransport = null
        discoveredPairingRssi = null

        val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
        val bondedAddresses = bondedDevices.map { it.address }.toSet()
        Log.d(TAG, "Starting early Classic BT discovery. Bonded addresses to skip: $bondedAddresses")

        classicDiscoveryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister previous discovery receiver", e)
            }
        }

        val discoveryReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(
                                        BluetoothDevice.EXTRA_DEVICE,
                                        BluetoothDevice::class.java,
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }

                            device?.let {
                                val deviceName = it.name ?: return
                                val isAlreadyBonded = bondedAddresses.contains(it.address)

                                Log.d(
                                    TAG,
                                    "Early discovery found: $deviceName (${it.address}), " +
                                        "bondState=${it.bondState}, isAlreadyBonded=$isAlreadyBonded",
                                )

                                if (deviceName.startsWith("RNode", ignoreCase = true) && !isAlreadyBonded) {
                                    Log.i(TAG, "Early discovery found unbonded RNode: $deviceName - storing for later pairing")
                                    discoveredPairingDevice = it
                                    discoveredPairingTransport = 1
                                    bluetoothAdapter?.cancelDiscovery()

                                    viewModelScope.launch {
                                        _state.update { state ->
                                            state.copy(usbPairingStatus = "Found $deviceName - enter PIN to pair")
                                        }
                                    }

                                    try {
                                        context.unregisterReceiver(this)
                                        classicDiscoveryReceiver = null
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to unregister discovery receiver", e)
                                    }
                                }
                            }
                        }

                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            Log.d(TAG, "Early Classic Bluetooth discovery finished")
                            try {
                                context.unregisterReceiver(this)
                                classicDiscoveryReceiver = null
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to unregister discovery receiver", e)
                            }
                        }
                    }
                }
            }

        classicDiscoveryReceiver = discoveryReceiver

        val filter =
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(discoveryReceiver, filter)
        }

        Log.d(TAG, "Starting early Classic Bluetooth discovery for RNode (before PIN entry)")
        bluetoothAdapter?.startDiscovery()
    }

    private var earlyBleScanCallback: ScanCallback? = null

    @SuppressLint("MissingPermission")
    private fun startBleScanForPairingEarly() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "BLE scanner not available for early discovery")
            return
        }

        val bondedAddresses =
            bluetoothAdapter?.bondedDevices?.map { it.address }?.toSet() ?: emptySet()
        Log.d(TAG, "Starting early BLE scan. Bonded addresses to skip: $bondedAddresses")

        earlyBleScanCallback?.let {
            try {
                scanner.stopScan(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop previous early BLE scan", e)
            }
        }

        val scanSettings =
            ScanSettings
                .Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    val device = result.device
                    val deviceName = device.name ?: return

                    if (!deviceName.startsWith("RNode", ignoreCase = true)) return

                    val isAlreadyBonded = bondedAddresses.contains(device.address)

                    Log.d(
                        TAG,
                        "Early BLE scan found: $deviceName (${device.address}), " +
                            "bondState=${device.bondState}, isAlreadyBonded=$isAlreadyBonded",
                    )

                    if (!isAlreadyBonded) {
                        Log.i(TAG, "Early BLE scan found unbonded RNode: $deviceName (RSSI: ${result.rssi}) - storing for later pairing")
                        discoveredPairingDevice = device
                        discoveredPairingTransport = 2
                        discoveredPairingRssi = result.rssi

                        try {
                            scanner.stopScan(this)
                            earlyBleScanCallback = null
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to stop early BLE scan", e)
                        }

                        bluetoothAdapter?.cancelDiscovery()

                        viewModelScope.launch {
                            _state.update { state ->
                                state.copy(usbPairingStatus = "Found $deviceName - enter PIN to pair")
                            }
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e(TAG, "Early BLE scan failed with error code: $errorCode")
                }
            }

        earlyBleScanCallback = callback

        try {
            scanner.startScan(null, scanSettings, callback)
            Log.d(TAG, "Early BLE scan started for RNode (before PIN entry)")

            viewModelScope.launch {
                delay(30_000L)
                earlyBleScanCallback?.let {
                    try {
                        scanner.stopScan(it)
                        earlyBleScanCallback = null
                        Log.d(TAG, "Early BLE scan timed out after 30 seconds")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to stop timed-out early BLE scan", e)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Early BLE scan permission denied", e)
        }
    }

    fun exitUsbBluetoothPairingMode() {
        viewModelScope.launch {
            stopAllPairingScans()

            withContext(Dispatchers.IO) {
                val stopPairingCmd =
                    byteArrayOf(
                        0xC0.toByte(),
                        0x46.toByte(),
                        0x00.toByte(),
                        0xC0.toByte(),
                    )
                val restartBtCmd =
                    byteArrayOf(
                        0xC0.toByte(),
                        0x46.toByte(),
                        0x01.toByte(),
                        0xC0.toByte(),
                    )
                usbBridge.write(stopPairingCmd)
                delay(100)
                usbBridge.write(restartBtCmd)
                delay(100)
                usbBridge.disconnect()
            }
            _state.update {
                it.copy(
                    isUsbPairingMode = false,
                    usbBluetoothPin = null,
                )
            }
        }
    }

    fun clearUsbError() {
        _state.update { it.copy(usbScanError = null) }
    }

    fun updateManualPinInput(pin: String) {
        val filtered = pin.filter { it.isDigit() }.take(6)
        _state.update { it.copy(manualPinInput = filtered) }
    }

    @SuppressLint("MissingPermission")
    fun submitManualPin() {
        val pin = _state.value.manualPinInput
        if (pin.length != 6) {
            _state.update {
                it.copy(usbScanError = "PIN must be 6 digits")
            }
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "Manual PIN submitted: $pin")
            _state.update {
                it.copy(
                    showManualPinEntry = false,
                    manualPinInput = "",
                    usbBluetoothPin = pin,
                )
            }

            withContext(Dispatchers.IO) {
                usbBridge.disconnect()
            }

            val preDiscoveredDevice = discoveredPairingDevice
            if (preDiscoveredDevice != null) {
                Log.i(TAG, "Using pre-discovered RNode: ${preDiscoveredDevice.name} (${preDiscoveredDevice.address})")
                pairWithDiscoveredDevice(preDiscoveredDevice, pin)
            } else {
                Log.w(TAG, "No pre-discovered device available, starting new discovery")
                initiateAutoPairingWithPin(pin)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun pairWithDiscoveredDevice(
        device: BluetoothDevice,
        pin: String,
    ) {
        val deviceName = device.name ?: "RNode"
        Log.d(TAG, "Pairing with pre-discovered device: $deviceName (${device.address})")

        _state.update { it.copy(usbPairingStatus = "Pairing with $deviceName...") }

        pairingHandler?.unregister()

        val handler =
            BlePairingHandler(context).apply {
                setAutoPairPin(pin, device.address)
                register()
            }
        pairingHandler = handler

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

                    val bondedDevice: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    if (bondedDevice?.address != device.address) return

                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    val prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                    Log.d(TAG, "Bond state changed for ${device.name}: $prevState -> $bondState")

                    when (bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            Log.i(TAG, "Successfully paired with ${device.name}!")
                            viewModelScope.launch {
                                val discoveredNode =
                                    DiscoveredRNode(
                                        name = deviceName,
                                        address = device.address,
                                        type = if (device.type == BluetoothDevice.DEVICE_TYPE_LE) BluetoothType.BLE else BluetoothType.CLASSIC,
                                        rssi = discoveredPairingRssi,
                                        isPaired = true,
                                        bluetoothDevice = device,
                                    )
                                _state.update { state ->
                                    state.copy(
                                        usbPairingStatus = "Successfully paired with $deviceName!",
                                        isUsbPairingMode = false,
                                        usbBluetoothPin = null,
                                        discoveredDevices = state.discoveredDevices.filter { it.address != device.address } + discoveredNode,
                                        selectedDevice = discoveredNode,
                                        interfaceName = defaultInterfaceNameFor(state, discoveredNode),
                                    )
                                }
                            }
                            try {
                                context.unregisterReceiver(this)
                                bondReceiver = null
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to unregister bond receiver", e)
                            }
                            pairingHandler?.unregister()
                            pairingHandler = null
                        }
                        BluetoothDevice.BOND_NONE -> {
                            Log.e(TAG, "Pairing failed with ${device.name}")
                            viewModelScope.launch {
                                _state.update {
                                    it.copy(usbPairingStatus = "Pairing failed with $deviceName")
                                }
                            }
                            try {
                                context.unregisterReceiver(this)
                                bondReceiver = null
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to unregister bond receiver", e)
                            }
                            pairingHandler?.unregister()
                            pairingHandler = null
                        }
                        BluetoothDevice.BOND_BONDING -> {
                            Log.d(TAG, "Bonding in progress with ${device.name}...")
                        }
                    }
                }
            }
        bondReceiver = receiver

        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        viewModelScope.launch {
            try {
                val createBondMethod =
                    BluetoothDevice::class.java.getMethod(
                        "createBond",
                        Int::class.javaPrimitiveType,
                    )
                val transport =
                    discoveredPairingTransport ?: when (device.type) {
                        BluetoothDevice.DEVICE_TYPE_LE -> 2
                        BluetoothDevice.DEVICE_TYPE_CLASSIC -> 1
                        BluetoothDevice.DEVICE_TYPE_DUAL -> 2
                        else -> 1
                    }
                val transportName = if (transport == 2) "TRANSPORT_LE" else "TRANSPORT_BREDR"
                val source = if (discoveredPairingTransport != null) "discovery" else "device.type"
                Log.d(TAG, "Using $transportName (from $source, device.type=${device.type})")
                createBondMethod.invoke(device, transport)
                Log.d(TAG, "createBond($transportName) called for pre-discovered RNode")
            } catch (e: Exception) {
                Log.w(TAG, "createBond with transport failed, using default", e)
                device.createBond()
            }
        }

        discoveredPairingDevice = null
        discoveredPairingTransport = null
        discoveredPairingRssi = null
    }

    fun cancelManualPinEntry() {
        viewModelScope.launch {
            stopAllPairingScans()

            _state.update {
                it.copy(
                    showManualPinEntry = false,
                    manualPinInput = "",
                    isUsbPairingMode = false,
                    usbPairingStatus = null,
                )
            }
            withContext(Dispatchers.IO) {
                usbBridge.disconnect()
            }
        }
    }

    // ========== USB-ASSISTED BLUETOOTH PAIRING (from Bluetooth tab) ==========

    fun startUsbAssistedPairing() {
        viewModelScope.launch {
            Log.d(TAG, "USB-assisted pairing from Bluetooth tab: scanning for USB devices")

            _state.update {
                it.copy(
                    usbScanError = null,
                    usbPairingStatus = "Scanning for USB devices...",
                )
            }

            try {
                val devices =
                    withContext(Dispatchers.IO) {
                        usbBridge.getConnectedUsbDevices()
                    }

                val usbDevices =
                    devices.map { device ->
                        DiscoveredUsbDevice(
                            deviceId = device.deviceId,
                            vendorId = device.vendorId,
                            productId = device.productId,
                            deviceName = device.deviceName,
                            manufacturerName = device.manufacturerName,
                            productName = device.productName,
                            serialNumber = device.serialNumber,
                            driverType = device.driverType,
                            hasPermission = usbBridge.hasPermission(device.deviceId),
                        )
                    }

                if (usbDevices.isEmpty()) {
                    _state.update {
                        it.copy(
                            usbPairingStatus = null,
                            pairingError = "No USB devices found. Connect your RNode via USB cable.",
                        )
                    }
                    return@launch
                }

                val usbDevice = usbDevices.first()
                Log.d(TAG, "USB-assisted pairing: Found USB device ${usbDevice.deviceId}")

                if (!usbDevice.hasPermission) {
                    _state.update {
                        it.copy(
                            selectedUsbDevice = usbDevice,
                            isRequestingUsbPermission = true,
                            usbPairingStatus = "Requesting USB permission...",
                        )
                    }
                    usbBridge.requestPermission(usbDevice.deviceId) { granted ->
                        viewModelScope.launch {
                            _state.update { it.copy(isRequestingUsbPermission = false) }
                            if (granted) {
                                val updatedDevice = usbDevice.copy(hasPermission = true)
                                _state.update { it.copy(selectedUsbDevice = updatedDevice) }
                                enterUsbBluetoothPairingMode()
                            } else {
                                _state.update {
                                    it.copy(
                                        usbPairingStatus = null,
                                        pairingError = "USB permission denied. Please grant permission.",
                                    )
                                }
                            }
                        }
                    }
                    return@launch
                }

                _state.update { it.copy(selectedUsbDevice = usbDevice) }
                enterUsbBluetoothPairingMode()
            } catch (e: Exception) {
                Log.e(TAG, "USB-assisted pairing failed", e)
                _state.update {
                    it.copy(
                        usbPairingStatus = null,
                        pairingError = "USB-assisted pairing failed: ${e.message}",
                    )
                }
            }
        }
    }

    private var pairingHandler: BlePairingHandler? = null

    @SuppressLint("MissingPermission")
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun initiateBluetoothPairing(device: DiscoveredRNode) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isPairingInProgress = true,
                    pairingError = null,
                    pairingTimeRemaining = 0,
                    lastPairingDeviceAddress = device.address,
                )
            }

            pairingHandler = BlePairingHandler(context).also { it.register() }

            try {
                val btDevice =
                    device.bluetoothDevice
                        ?: bluetoothAdapter?.getRemoteDevice(device.address)
                if (btDevice != null && btDevice.bondState != BluetoothDevice.BOND_BONDED) {
                    btDevice.createBond()

                    val startTime = System.currentTimeMillis()
                    var pairingStarted = false
                    while (System.currentTimeMillis() - startTime < PAIRING_START_TIMEOUT_MS) {
                        val bondState = btDevice.bondState
                        if (bondState == BluetoothDevice.BOND_BONDING) {
                            pairingStarted = true
                            break
                        }
                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            pairingStarted = true
                            break
                        }
                        delay(200)
                    }

                    if (!pairingStarted && btDevice.bondState == BluetoothDevice.BOND_NONE) {
                        if (device.type == BluetoothType.BLE) {
                            Log.d(TAG, "Device not responding, waiting for reconnect: ${device.name}")
                            _state.update {
                                it.copy(
                                    isPairingInProgress = false,
                                    isWaitingForReconnect = true,
                                    reconnectDeviceName = device.name,
                                )
                            }

                            val reconnectedDevice = scanForDeviceByName(device.name, device.type)

                            _state.update {
                                it.copy(isWaitingForReconnect = false, reconnectDeviceName = null)
                            }

                            if (reconnectedDevice != null) {
                                Log.d(TAG, "Device reconnected, retrying pairing: ${device.name}")
                                _state.update { state ->
                                    state.copy(
                                        discoveredDevices =
                                            state.discoveredDevices.map {
                                                if (it.name == device.name) reconnectedDevice else it
                                            },
                                    )
                                }
                                initiateBluetoothPairing(reconnectedDevice)
                                return@launch
                            } else {
                                _state.update {
                                    it.copy(
                                        pairingError =
                                            "Could not find RNode. Please ensure the device is powered on, " +
                                                "Bluetooth is enabled, and it's within range. " +
                                                "Tap 'Scan Again' to refresh the device list.",
                                    )
                                }
                                return@launch
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    pairingError =
                                        "RNode is not in pairing mode. Press the " +
                                            "pairing button until a PIN code appears on the display.",
                                )
                            }
                            return@launch
                        }
                    }

                    val pinStartTime = System.currentTimeMillis()
                    while (btDevice.bondState == BluetoothDevice.BOND_BONDING) {
                        val elapsed = System.currentTimeMillis() - pinStartTime
                        if (elapsed >= PIN_ENTRY_TIMEOUT_MS) break
                        delay(500)
                    }

                    when (btDevice.bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            val updatedDevice = device.copy(isPaired = true)
                            _state.update { state ->
                                state.copy(
                                    selectedDevice = updatedDevice,
                                    discoveredDevices =
                                        state.discoveredDevices.map {
                                            if (it.address == device.address) updatedDevice else it
                                        },
                                    interfaceName = defaultInterfaceNameFor(state, updatedDevice),
                                )
                            }
                            Log.d(TAG, "Pairing successful for ${device.name}")
                        }
                        BluetoothDevice.BOND_NONE -> {
                            _state.update {
                                it.copy(
                                    pairingError =
                                        "Pairing was cancelled or the PIN was " +
                                            "incorrect. Try again and enter the PIN shown " +
                                            "on the RNode.",
                                )
                            }
                        }
                        BluetoothDevice.BOND_BONDING -> {
                            Log.w(TAG, "Pairing still in progress after timeout")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed", e)
                _state.update { it.copy(pairingError = e.message ?: "Pairing failed") }
            } finally {
                pairingHandler?.unregister()
                pairingHandler = null
                _state.update { it.copy(isPairingInProgress = false, pairingTimeRemaining = 0) }
            }
        }
    }

    fun clearPairingError() {
        _state.update { it.copy(pairingError = null) }
    }

    fun retryPairing() {
        val address = _state.value.lastPairingDeviceAddress ?: return
        val device = _state.value.discoveredDevices.find { it.address == address } ?: return
        clearPairingError()
        initiateBluetoothPairing(device)
    }

    // ========== STEP 2: REGION SELECTION ==========

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun selectCountry(country: String?) {
        _state.update {
            it.copy(
                selectedCountry = country,
                selectedPreset = null,
                isCustomMode = false,
            )
        }
    }

    fun selectPreset(preset: RNodeRegionalPreset) {
        _state.update {
            it.copy(
                selectedPreset = preset,
                isCustomMode = false,
                frequency = preset.frequency.toString(),
                frequencyError = null,
                bandwidth = preset.bandwidth.toString(),
                bandwidthError = null,
                spreadingFactor = preset.spreadingFactor.toString(),
                spreadingFactorError = null,
                codingRate = preset.codingRate.toString(),
                codingRateError = null,
                txPower = preset.txPower.toString(),
                txPowerError = null,
            )
        }
    }

    fun enableCustomMode() {
        _state.update {
            it.copy(
                isCustomMode = true,
                selectedPreset = null,
                selectedFrequencyRegion = null,
            )
        }
        updateRegulatoryWarning()
    }

    private fun updateRegulatoryWarning() {
        val state = _state.value
        val region = state.selectedFrequencyRegion
        val stAlock = state.stAlock.toDoubleOrNull()
        val ltAlock = state.ltAlock.toDoubleOrNull()
        val isCustomMode = state.isCustomMode

        val (showWarning, message) =
            when {
                isCustomMode && region == null ->
                    true to "No region selected. You are responsible for ensuring compliance with local regulations."

                region != null && region.dutyCycle < 100 && (stAlock == null || ltAlock == null) ->
                    true to "Region ${region.name} has a ${region.dutyCycle}% duty cycle limit. " +
                        "Airtime limits are not set. Ensure compliance with local regulations."

                else -> false to null
            }

        _state.update {
            it.copy(
                showRegulatoryWarning = showWarning,
                regulatoryWarningMessage = message,
            )
        }
    }

    // ========== FREQUENCY REGION SELECTION ==========

    fun selectFrequencyRegion(region: FrequencyRegion) {
        userModifiedFields.clear()
        _state.update {
            it.copy(
                selectedFrequencyRegion = region,
                selectedPreset = null,
                isCustomMode = false,
            )
        }
        applyFrequencyRegionSettings()
    }

    fun getFrequencyRegions(): List<FrequencyRegion> = FrequencyRegions.regions

    fun getRegionLimits(): RegionLimits? {
        val region = _state.value.selectedFrequencyRegion ?: return null
        return RegionLimits(
            maxTxPower = region.maxTxPower,
            minFrequency = region.frequencyStart,
            maxFrequency = region.frequencyEnd,
            dutyCycle = region.dutyCycle,
        )
    }

    fun togglePopularPresets() {
        _state.update { it.copy(showPopularPresets = !it.showPopularPresets) }
    }

    // ========== MODEM PRESET SELECTION ==========

    fun selectModemPreset(preset: ModemPreset) {
        _state.update { it.copy(selectedModemPreset = preset) }
    }

    fun getModemPresets(): List<ModemPreset> = ModemPreset.entries

    fun getFilteredCountries(): List<String> {
        val query = _state.value.searchQuery.lowercase()
        return RNodeRegionalPresets.getCountries().filter {
            it.lowercase().contains(query)
        }
    }

    fun getPresetsForSelectedCountry(): List<RNodeRegionalPreset> {
        val country = _state.value.selectedCountry ?: return emptyList()
        return RNodeRegionalPresets.getPresetsForCountry(country)
    }

    // ========== STEP 5: REVIEW & CONFIGURE ==========

    fun updateInterfaceName(name: String) {
        _state.update { it.copy(interfaceName = name, nameError = null) }
    }

    fun updateFrequency(value: String) {
        userModifiedFields.add("frequency")
        val region = _state.value.selectedFrequencyRegion
        val result = RNodeConfigValidator.validateFrequency(value, region)
        _state.update { it.copy(frequency = value, frequencyError = result.errorMessage) }
    }

    fun updateBandwidth(value: String) {
        val result = RNodeConfigValidator.validateBandwidth(value)
        _state.update { it.copy(bandwidth = value, bandwidthError = result.errorMessage) }
    }

    fun updateSpreadingFactor(value: String) {
        val result = RNodeConfigValidator.validateSpreadingFactor(value)
        _state.update { it.copy(spreadingFactor = value, spreadingFactorError = result.errorMessage) }
    }

    fun updateCodingRate(value: String) {
        val result = RNodeConfigValidator.validateCodingRate(value)
        _state.update { it.copy(codingRate = value, codingRateError = result.errorMessage) }
    }

    fun updateTxPower(value: String) {
        userModifiedFields.add("txPower")
        val region = _state.value.selectedFrequencyRegion
        val result = RNodeConfigValidator.validateTxPower(value, region)
        _state.update { it.copy(txPower = value, txPowerError = result.errorMessage) }
    }

    fun updateStAlock(value: String) {
        userModifiedFields.add("stAlock")
        val region = _state.value.selectedFrequencyRegion
        val result = RNodeConfigValidator.validateAirtimeLimit(value, region)
        _state.update { it.copy(stAlock = value, stAlockError = result.errorMessage) }
        updateRegulatoryWarning()
    }

    fun updateLtAlock(value: String) {
        userModifiedFields.add("ltAlock")
        val region = _state.value.selectedFrequencyRegion
        val result = RNodeConfigValidator.validateAirtimeLimit(value, region)
        _state.update { it.copy(ltAlock = value, ltAlockError = result.errorMessage) }
        updateRegulatoryWarning()
    }

    fun updateInterfaceMode(mode: String) {
        _state.update { it.copy(interfaceMode = mode) }
    }

    fun toggleAdvancedSettings() {
        _state.update { it.copy(showAdvancedSettings = !it.showAdvancedSettings) }
    }

    private fun validateConfigurationSilent(): Boolean {
        val state = _state.value
        return RNodeConfigValidator.validateConfigSilent(
            name = state.interfaceName,
            frequency = state.frequency,
            bandwidth = state.bandwidth,
            spreadingFactor = state.spreadingFactor,
            codingRate = state.codingRate,
            txPower = state.txPower,
            stAlock = state.stAlock,
            ltAlock = state.ltAlock,
            region = state.selectedFrequencyRegion,
        )
    }

    private fun validateConfiguration(): Boolean {
        val state = _state.value
        val result =
            RNodeConfigValidator.validateConfig(
                name = state.interfaceName,
                frequency = state.frequency,
                bandwidth = state.bandwidth,
                spreadingFactor = state.spreadingFactor,
                codingRate = state.codingRate,
                txPower = state.txPower,
                stAlock = state.stAlock,
                ltAlock = state.ltAlock,
                region = state.selectedFrequencyRegion,
            )

        _state.update {
            it.copy(
                nameError = result.nameError,
                frequencyError = result.frequencyError,
                bandwidthError = result.bandwidthError,
                spreadingFactorError = result.spreadingFactorError,
                codingRateError = result.codingRateError,
                txPowerError = result.txPowerError,
                stAlockError = result.stAlockError,
                ltAlockError = result.ltAlockError,
            )
        }

        return result.isValid
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun saveConfiguration() {
        if (!validateConfiguration()) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }

            try {
                val state = _state.value

                // Determine connection parameters based on connection type
                val deviceName: String
                val connectionMode: String

                when (state.connectionType) {
                    RNodeConnectionType.TCP_WIFI -> {
                        val host = state.tcpHost.trim()
                        val port = state.tcpPort.toIntOrNull() ?: 7633
                        deviceName = "$host:$port"
                        connectionMode = "tcp"
                    }
                    RNodeConnectionType.USB_SERIAL -> {
                        deviceName = ""
                        connectionMode = "usb"
                    }
                    RNodeConnectionType.BLUETOOTH -> {
                        val (name, mode) =
                            if (state.selectedDevice != null) {
                                state.selectedDevice.name to
                                    when (state.selectedDevice.type) {
                                        BluetoothType.CLASSIC -> "classic"
                                        BluetoothType.BLE -> "ble"
                                        BluetoothType.UNKNOWN -> "classic"
                                    }
                            } else {
                                state.manualDeviceName to
                                    when (state.manualBluetoothType) {
                                        BluetoothType.CLASSIC -> "classic"
                                        BluetoothType.BLE -> "ble"
                                        BluetoothType.UNKNOWN -> "classic"
                                    }
                            }
                        deviceName = name
                        connectionMode = mode
                    }
                }

                val result =
                    RNodeWizardResult(
                        name = state.interfaceName.trim(),
                        connectionMode = connectionMode,
                        targetDevice = deviceName,
                        frequency = state.frequency.toLongOrNull() ?: 915000000,
                        bandwidth = state.bandwidth.toIntOrNull() ?: 125000,
                        spreadingFactor = state.spreadingFactor.toIntOrNull() ?: 8,
                        codingRate = state.codingRate.toIntOrNull() ?: 5,
                        txPower = state.txPower.toIntOrNull() ?: 7,
                        interfaceMode = state.interfaceMode,
                        stAlock = state.stAlock,
                        ltAlock = state.ltAlock,
                    )

                _state.update {
                    it.copy(
                        savedResult = result,
                        saveSuccess = true,
                        isSaving = false,
                    )
                }

                Log.d(TAG, "Created RNode wizard result: ${result.name} ($connectionMode -> $deviceName)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save configuration", e)
                _state.update {
                    it.copy(
                        saveError = e.message ?: "Failed to save configuration",
                        isSaving = false,
                    )
                }
            }
        }
    }

    fun clearSaveError() {
        _state.update { it.copy(saveError = null) }
    }

    /**
     * Get the effective device name for display.
     * For TCP mode, shows the host:port. For USB, shows USB device name. For Bluetooth, shows device name.
     */
    fun getEffectiveDeviceName(): String {
        val state = _state.value
        return when (state.connectionType) {
            RNodeConnectionType.TCP_WIFI -> {
                val port = state.tcpPort.toIntOrNull() ?: 7633
                if (state.tcpHost.isNotBlank()) {
                    if (port == 7633) state.tcpHost else "${state.tcpHost}:$port"
                } else {
                    "No host specified"
                }
            }
            RNodeConnectionType.USB_SERIAL -> {
                state.selectedUsbDevice?.let { device ->
                    device.productName
                        ?: device.manufacturerName?.let { "$it Device" }
                        ?: device.driverType
                } ?: "No USB device selected"
            }
            RNodeConnectionType.BLUETOOTH -> {
                state.selectedDevice?.name
                    ?: state.manualDeviceName.ifBlank { "No device selected" }
            }
        }
    }

    fun getEffectiveBluetoothType(): BluetoothType? {
        val state = _state.value
        return when (state.connectionType) {
            RNodeConnectionType.TCP_WIFI -> null
            RNodeConnectionType.USB_SERIAL -> null
            RNodeConnectionType.BLUETOOTH -> state.selectedDevice?.type ?: state.manualBluetoothType
        }
    }

    fun getConnectionTypeString(): String {
        val state = _state.value
        return when (state.connectionType) {
            RNodeConnectionType.TCP_WIFI -> "WiFi / TCP"
            RNodeConnectionType.USB_SERIAL -> "USB Serial"
            RNodeConnectionType.BLUETOOTH -> {
                when (state.selectedDevice?.type ?: state.manualBluetoothType) {
                    BluetoothType.CLASSIC -> "Bluetooth Classic"
                    BluetoothType.BLE -> "Bluetooth LE"
                    BluetoothType.UNKNOWN -> "Bluetooth"
                }
            }
        }
    }

    fun isTcpMode(): Boolean = _state.value.connectionType == RNodeConnectionType.TCP_WIFI

    fun isUsbMode(): Boolean = _state.value.connectionType == RNodeConnectionType.USB_SERIAL

    override fun onCleared() {
        super.onCleared()
        stopAllPairingScans()
        Log.d(TAG, "RNodeWizardViewModel cleared")
    }
}
