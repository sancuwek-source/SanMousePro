package com.san.mousepro;

import com.san.mousepro.IInputCallback;

interface IInputService {
    void destroy() = 16777114;
    void start(String configJson, String libDir, IInputCallback cb) = 1;
    void stop() = 2;
    void setConfig(String configJson) = 3;
    void setActive(boolean active) = 4;
}
