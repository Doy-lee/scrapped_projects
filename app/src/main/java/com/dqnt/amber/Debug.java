package com.dqnt.amber;

import android.util.Log;

public class Debug {
    final static boolean DEBUG_MODE = true;
    final static boolean RESET_DB = false;
    final static boolean RESET_CONFIG = false;

    static boolean CAREFUL_ASSERT(boolean flag, String tag, String log) {
        if (!flag) {
            Log.e(tag, log);
            if (DEBUG_MODE) throw new RuntimeException();
        }

        return flag;
    }

}
