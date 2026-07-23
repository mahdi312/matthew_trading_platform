/**
 * Browser shims for Node-oriented packages (sockjs-client expects `global`).
 * Must load before any app chunks that import sockjs-client.
 */
(window as unknown as { global: Window }).global = window;
