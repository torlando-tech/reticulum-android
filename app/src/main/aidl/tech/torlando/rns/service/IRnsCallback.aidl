package tech.torlando.rns.service;

/** Callback from RNS service process to UI process. */
oneway interface IRnsCallback {
    /** Called when service state or stats change. JSON payload. */
    void onUpdate(String json);
}
