package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Detects provider-side error payloads that still return HTTP 200.
 * Shapes taken from {@code docs/report.html} Newman collection runs.
 */
public final class ApiErrorDetector {

    private ApiErrorDetector() {}

    public static boolean isErrorPayload(JsonObject root) {
        if (root == null) return true;
        if (root.has("Note") || root.has("Information") || root.has("Error Message")) {
            return true;
        }
        if (root.has("success") && !root.get("success").getAsBoolean()) {
            return true;
        }
        if (root.has("error") && root.get("error").isJsonObject()) {
            return true;
        }
        if (root.has("error") && root.get("error").isJsonPrimitive()) {
            return true;
        }
        JsonElement statusElement = root.get("status");
        if (statusElement != null && statusElement.isJsonPrimitive()) {
            String status = statusElement.getAsString();
            if ("ERROR".equalsIgnoreCase(status)) {
                return true;
            }
        }
        if (root.has("code") && root.get("code").isJsonPrimitive()) {
            try {
                int code = root.get("code").getAsInt();
                if (code >= 400) return true;
            } catch (Exception ignored) {
                // e.g. Twelve Data string codes — handled below
            }
        }
        if (root.has("message") && root.has("code") && !root.has("meta")) {
            // Twelve Data: {"code":401,"message":"..."}
            return true;
        }
        if (root.has("quandl_error")) return true;
        if (root.has("Response") && "Error".equalsIgnoreCase(root.get("Response").getAsString())) {
            return true;
        }
        if (statusElement != null && statusElement.isJsonObject()) {
            JsonObject st = statusElement.getAsJsonObject();
            if (st.has("error_code") && st.get("error_code").getAsInt() != 0) {
                return true;
            }
        }
        return false;
    }
}
