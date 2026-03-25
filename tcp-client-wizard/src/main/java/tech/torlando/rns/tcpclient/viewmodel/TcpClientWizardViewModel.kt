package tech.torlando.rns.tcpclient.viewmodel

import androidx.lifecycle.ViewModel
import tech.torlando.rns.tcpclient.data.TcpClientWizardResult
import tech.torlando.rns.tcpclient.data.TcpCommunityServer
import tech.torlando.rns.tcpclient.data.TcpCommunityServers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TcpClientWizardStep {
    SERVER_SELECTION,
    REVIEW_CONFIGURE,
}

@androidx.compose.runtime.Immutable
data class TcpClientWizardState(
    val currentStep: TcpClientWizardStep = TcpClientWizardStep.SERVER_SELECTION,
    // Server selection
    val selectedServer: TcpCommunityServer? = null,
    val isCustomMode: Boolean = false,
    // Configuration fields
    val interfaceName: String = "",
    val targetHost: String = "",
    val targetPort: String = "",
    // Bootstrap interface option
    val bootstrapOnly: Boolean = false,
    // SOCKS5 proxy (Tor/Orbot) settings
    val socksProxyEnabled: Boolean = false,
    val socksProxyHost: String = "127.0.0.1",
    val socksProxyPort: String = "9050",
    // Save state
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
    val savedResult: TcpClientWizardResult? = null,
)

@Suppress("TooManyFunctions")
class TcpClientWizardViewModel : ViewModel() {

    private val _state = MutableStateFlow(TcpClientWizardState())
    val state: StateFlow<TcpClientWizardState> = _state.asStateFlow()

    /**
     * Set initial values when creating from a discovered interface.
     */
    fun setInitialValues(
        host: String,
        port: Int,
        name: String,
    ) {
        val matchingServer =
            TcpCommunityServers.servers.find { server ->
                server.host == host && server.port == port
            }

        val isOnion = host.endsWith(".onion")
        _state.update {
            it.copy(
                selectedServer = matchingServer,
                isCustomMode = matchingServer == null,
                interfaceName = name,
                targetHost = host,
                targetPort = port.toString(),
                bootstrapOnly = matchingServer?.isBootstrap ?: false,
                socksProxyEnabled = isOnion,
                currentStep = TcpClientWizardStep.REVIEW_CONFIGURE,
            )
        }
    }

    fun getCommunityServers(): List<TcpCommunityServer> = TcpCommunityServers.servers

    fun selectServer(server: TcpCommunityServer) {
        val isOnion = server.host.endsWith(".onion")
        _state.update {
            it.copy(
                selectedServer = server,
                isCustomMode = false,
                interfaceName = server.name,
                targetHost = server.host,
                targetPort = server.port.toString(),
                bootstrapOnly = server.isBootstrap,
                socksProxyEnabled = isOnion,
            )
        }
    }

    fun enableCustomMode() {
        _state.update {
            it.copy(
                selectedServer = null,
                isCustomMode = true,
                interfaceName = "",
                targetHost = "",
                targetPort = "",
                bootstrapOnly = false,
                socksProxyEnabled = false,
                socksProxyHost = "127.0.0.1",
                socksProxyPort = "9050",
            )
        }
    }

    fun toggleBootstrapOnly(enabled: Boolean) {
        _state.update { it.copy(bootstrapOnly = enabled) }
    }

    fun updateInterfaceName(value: String) {
        _state.update { it.copy(interfaceName = value) }
    }

    fun updateTargetHost(value: String) {
        val isOnion = value.trim().endsWith(".onion")
        _state.update {
            it.copy(
                targetHost = value,
                socksProxyEnabled = if (isOnion) true else it.socksProxyEnabled,
            )
        }
    }

    fun updateTargetPort(value: String) {
        _state.update { it.copy(targetPort = value) }
    }

    fun toggleSocksProxy(enabled: Boolean) {
        _state.update {
            val isOnion = it.targetHost.trim().endsWith(".onion")
            it.copy(socksProxyEnabled = enabled || isOnion)
        }
    }

    fun updateSocksProxyHost(value: String) {
        _state.update { it.copy(socksProxyHost = value) }
    }

    fun updateSocksProxyPort(value: String) {
        _state.update { it.copy(socksProxyPort = value) }
    }

    fun canProceed(): Boolean {
        val currentState = _state.value
        return when (currentState.currentStep) {
            TcpClientWizardStep.SERVER_SELECTION ->
                currentState.selectedServer != null || currentState.isCustomMode
            TcpClientWizardStep.REVIEW_CONFIGURE -> true
        }
    }

    fun goToNextStep() {
        val currentState = _state.value
        val nextStep =
            when (currentState.currentStep) {
                TcpClientWizardStep.SERVER_SELECTION -> TcpClientWizardStep.REVIEW_CONFIGURE
                TcpClientWizardStep.REVIEW_CONFIGURE -> return
            }
        _state.update { it.copy(currentStep = nextStep) }
    }

    fun goToPreviousStep() {
        val currentState = _state.value
        val previousStep =
            when (currentState.currentStep) {
                TcpClientWizardStep.SERVER_SELECTION -> return
                TcpClientWizardStep.REVIEW_CONFIGURE -> TcpClientWizardStep.SERVER_SELECTION
            }
        _state.update { it.copy(currentStep = previousStep) }
    }

    fun saveConfiguration() {
        _state.update { it.copy(isSaving = true, saveError = null) }

        val currentState = _state.value
        val interfaceName = currentState.interfaceName.trim().ifEmpty { "TCP Connection" }

        val result = TcpClientWizardResult(
            name = interfaceName,
            targetHost = currentState.targetHost.trim(),
            targetPort = currentState.targetPort.toIntOrNull() ?: 4242,
            bootstrapOnly = currentState.bootstrapOnly,
            socksProxyEnabled = currentState.socksProxyEnabled,
            socksProxyHost = currentState.socksProxyHost.trim().ifEmpty { "127.0.0.1" },
            socksProxyPort = currentState.socksProxyPort.toIntOrNull() ?: 9050,
        )

        _state.update {
            it.copy(
                isSaving = false,
                saveSuccess = true,
                savedResult = result,
            )
        }
    }

    fun clearSaveError() {
        _state.update { it.copy(saveError = null) }
    }
}
