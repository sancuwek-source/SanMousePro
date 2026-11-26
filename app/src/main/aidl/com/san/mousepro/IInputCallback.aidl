package com.san.mousepro;

oneway interface IInputCallback {
    void onMode(boolean gameMode);
    void onCursor(float x, float y);
    void onStatus(String msg);
}
