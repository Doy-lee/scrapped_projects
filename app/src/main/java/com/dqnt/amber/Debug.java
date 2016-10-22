package com.dqnt.amber;

import android.app.Activity;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

class Debug {
    final static boolean DEBUG_MODE = true;
    final static boolean RESET_DB = false;
    final static boolean RESET_CONFIG = false;

    private static String getMethodName(String className) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int i = 0;
        for (StackTraceElement element : stack) {
            if (element.getClassName().equals(className)) break;
            i++;
        }

        String result = stack[i].getMethodName();
        return result;
    }

    private static class DebugCounter {
        final String name;
        int callCount;

        DebugCounter(String name) {
            this.name = name;
            callCount = 1;
        }

        @Override
        public String toString() {
            return name + " called " + callCount + " times";
        }
    }

    private static class FunctionCounterSorter implements Comparator<DebugCounter> {
        @Override
        public int compare(DebugCounter o1, DebugCounter o2) {
            return o1.name.compareTo(o2.name);
        }
    }

    private static class FunctionCounterList extends ArrayList<DebugCounter> {
        @Override
        public int indexOf(Object o) {

            int first = 0;
            int last = this.size() - 1;
            int midpoint = (first + last) / 2;

            String nameCheck = (String) o;
            while (first <= last) {
                DebugCounter compare = this.get(midpoint);
                int result = nameCheck.compareTo(compare.name);

                if (result < 0) {
                    last = midpoint - 1;
                } else if (result > 0) {
                    first = midpoint + 1;
                } else {
                    return midpoint;
                }
                midpoint = (first + last) / 2;
            }

            return -1;
        }
    }

    private static FunctionCounterList functionCounter = null;
    private static FunctionCounterSorter functionCounterSorter = null;
    static void INCREMENT_COUNTER(Class c, String tag) {
        if (DEBUG_MODE) {
            if (functionCounter == null) functionCounter = new FunctionCounterList();
            if (functionCounterSorter == null) functionCounterSorter = new FunctionCounterSorter();

            final String className = c.getName();
            final String methodName = getMethodName(className);
            final String counterName = className + ":" + methodName + ":" + tag;

            int index = functionCounter.indexOf(counterName);
            if (index == -1) {
                DebugCounter counter = new DebugCounter(counterName);
                functionCounter.add(counter);
            } else {
                functionCounter.get(index).callCount++;
            }

            Collections.sort(functionCounter, functionCounterSorter);
        }
    }

    static String GENERATE_COUNTER_STRING() {
        String result = "";
        if (functionCounter == null) return result;

        for (int i = 0; i < functionCounter.size(); i++) {
            result += functionCounter.get(i) + "\n";
        }

        return result;
    }

    static void LOG_D(Class assertTag, String log) {
        String className = assertTag.getName();
        String methodName = getMethodName(className);
        String fullLog = methodName + "(): " + log;

        Log.d(className, fullLog);
    }

    static void LOG_E(Class assertTag, String log) {
        String className = assertTag.getName();
        String methodName = getMethodName(className);
        String fullLog = methodName + "(): " + log;

        Log.e(className, fullLog);
    }

    static void LOG_W(Class assertTag, String log) {
        String className = assertTag.getName();
        String methodName = getMethodName(className);
        String fullLog = methodName + "(): " + log;

        Log.w(className, fullLog);
    }

    static void LOG_I(Class assertTag, String log) {
        String className = assertTag.getName();
        String methodName = getMethodName(className);
        String fullLog = methodName + "(): " + log;

        Log.i(className, fullLog);
    }

    static void LOG_V(Class assertTag, String log) {
        String className = assertTag.getName();
        String methodName = getMethodName(className);
        String fullLog = methodName + "(): " + log;

        Log.v(className, fullLog);
    }

    // NOTE(doyle): Asserts condition is true if debug mode is on
    //              Log error message only if debug mode off
    static boolean CAREFUL_ASSERT(boolean flag, Class assertTag, String log) {
        if (!flag) {
            String className = assertTag.getName();
            String methodName = getMethodName(className);
            String fullLog = methodName + "(): " + log;

            Log.e(className, fullLog);
            if (DEBUG_MODE) throw new RuntimeException();
        }
        return flag;
    }

}
