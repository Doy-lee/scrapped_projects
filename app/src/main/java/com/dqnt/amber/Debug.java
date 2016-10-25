package com.dqnt.amber;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class Debug {
    final static boolean DEBUG_MODE = true;
    final static boolean RESET_DB = false;
    final static boolean RESET_CONFIG = false;

    private static String getMethodName(String className) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int i = 0;
        boolean matched = false;
        for (StackTraceElement element : stack) {
            if (element.getClassName().equals(className)) {
                matched = true;
                break;
            }
            i++;
        }

        String result;
        if (matched) {
            result = stack[i].getMethodName();
        } else {
            result = null;
        }

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
    static void INCREMENT_COUNTER(Object object, String tag) {
        if (DEBUG_MODE) {
            if (functionCounter == null) functionCounter = new FunctionCounterList();
            if (functionCounterSorter == null) functionCounterSorter = new FunctionCounterSorter();

            final FunctionTag functionTag = generateFunctionTag(object);
            final String counterName = functionTag.className + ":"
                                       + functionTag.methodName + ":" + tag;

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

    private static class FunctionTag {
        String className;
        String methodName;
    }

    private static FunctionTag generateFunctionTag(Object object) {
        FunctionTag result = new FunctionTag();
        String className;

        if (object instanceof Class) {
            className = ((Class)object).getName();
        } else {
            className = object.getClass().getName();
        }
        result.className = className;

        String methodName = getMethodName(className);
        if (methodName == null) {
            methodName = "Unresolvable";
        }
        result.methodName = methodName + "(): ";

        return result;
    }

    static void LOG_D(Object object, String log) {
        FunctionTag tag = generateFunctionTag(object);
        Log.d(tag.className, tag.methodName + log);
    }

    static void LOG_E(Object object, String log) {
        FunctionTag tag = generateFunctionTag(object);
        Log.e(tag.className, tag.methodName + log);
    }

    static void LOG_W(Object object, String log) {
        FunctionTag tag = generateFunctionTag(object);
        Log.w(tag.className, tag.methodName + log);
    }

    static void LOG_I(Object object, String log) {
        FunctionTag tag = generateFunctionTag(object);
        Log.i(tag.className, tag.methodName + log);
    }

    static void LOG_V(Object object, String log) {
        FunctionTag tag = generateFunctionTag(object);
        Log.v(tag.className, tag.methodName + log);
    }

    // NOTE(doyle): Asserts condition is true if debug mode is on
    //              Log error message only if debug mode off
    static boolean CAREFUL_ASSERT(boolean flag, Object object, String log) {
        if (!flag) {
            LOG_E(object, log);
            if (DEBUG_MODE) throw new RuntimeException();
        }
        return flag;
    }

    static void ASSERT(boolean flag) {
        if (!flag) throw new RuntimeException();
    }

    static void TOAST(Context context, String log, int length) {
        Toast.makeText(context, log, length).show();
    }


    private static int debugViewId;
    private static void initViewForActivity(Activity activity) {
        String name = activity.getComponentName().getClassName();
        RelativeLayout debugLayout = new RelativeLayout(activity);
        RelativeLayout.LayoutParams debugLayoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);

        // NOTE: Offset beyond the android status bar so debug layout doesn't get covered.
        // If we reaalllly care, then TODO: calculate the actual height
        debugLayout.setPadding(0, 100, 0, 0);

        LinearLayout debugStringLayout = new LinearLayout(activity);
        LinearLayout.LayoutParams debugStringLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        debugStringLayout.setOrientation(LinearLayout.VERTICAL);

        debugViewId = View.generateViewId();
        debugStringLayout.setId(debugViewId);
        debugLayout.addView(debugStringLayout, debugStringLayoutParams);

        activity.addContentView(debugLayout, debugLayoutParams);
    }


    abstract static class UiUpdateAndRender implements Runnable {
        private WeakReference<Activity> weakActivity;
        private WeakReference<Handler> weakHandler;
        private float updateRateInMilliseconds;
        boolean isRunning;

        UiUpdateAndRender(Activity activity, Handler handler, int updateRateInSeconds) {
            ASSERT(activity != null);
            ASSERT(handler != null);
            ASSERT(updateRateInSeconds > 0);

            this.weakActivity = new WeakReference<Activity>(activity);
            this.weakHandler = new WeakReference<Handler>(handler);
            updateRateInMilliseconds = 1000 / updateRateInSeconds;

            initViewForActivity(activity);

            isRunning = true;
            activity.runOnUiThread(this);
        }

        @Override
        public void run() {
            Activity activity = weakActivity.get();
            Handler handler = weakHandler.get();
            if (activity != null && handler != null) {
                clearView(activity);
                if (isRunning) {
                    renderElements();
                }
                handler.postDelayed(this, (long) updateRateInMilliseconds);
            }
        }

        private void clearView(Activity activity) {
            LinearLayout view = (LinearLayout) activity.findViewById(debugViewId);
            if (view == null) return;

            view.removeAllViewsInLayout();
            view.invalidate();
        }

        void pushVariable(String name, Object value) {
            Activity activity = weakActivity.get();
            if (activity != null && value != null) {
                LinearLayout view = (LinearLayout) activity.findViewById(debugViewId);
                if (view != null) {
                    String debugString = name + ": ";
                    String debugValue = "--";

                    if (value instanceof Integer) {
                        int tmp = (int) value;
                        debugValue = String.valueOf(tmp);

                    } else if (value instanceof Boolean) {
                        boolean tmp = (boolean) value;
                        if (tmp) debugValue = "true";
                        else     debugValue = "false";

                    } else {
                        CAREFUL_ASSERT(false, Debug.class, "Debug type not handled yet");
                    }

                    createAndRenderDebugText(activity, view, debugString, debugValue);
                }
            }
        }

        void pushText(String debugString) {
            Activity activity = weakActivity.get();
            if (activity != null && debugString != null && !debugString.isEmpty()) {
                LinearLayout view = (LinearLayout) activity.findViewById(debugViewId);
                if (view != null) {
                    createAndRenderDebugText(activity, view, debugString, null);
                }
            }
        }

        void pushClass(Object object, boolean ignoreStatic, boolean ignoreFinal) {
            Activity activity = weakActivity.get();
            if (activity != null && object != null) {
                LinearLayout view = (LinearLayout) activity.findViewById(debugViewId);
                if (view != null) {
                    Field[] fields = object.getClass().getDeclaredFields();
                    for (Field field: fields) {
                        try {
                            String variableName = field.getName() + ": ";
                            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                                && ignoreStatic) {

                            } else if (java.lang.reflect.Modifier.isFinal(field.getModifiers())
                                       && ignoreFinal) {

                            } else {
                                Object objectValue = field.get(object);
                                if (objectValue != null) {
                                    String variableValue = field.get(object).toString();
                                    createAndRenderDebugText(activity, view, variableName,
                                                             variableValue);
                                }
                            }
                        } catch (IllegalAccessException e) {
                            // NOTE: Not important, just let it print what it can
                            // e.printStackTrace();
                        }
                    }
                }
            }
        }

        private void createAndRenderDebugText(Activity activity, LinearLayout view,
                                              String debugName, String debugValue) {

            LinearLayout debugStringLayout = new LinearLayout(activity);
            LinearLayout.LayoutParams debugStringLayoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            debugStringLayout.setLayoutParams(debugStringLayoutParams);

            int padding = 10;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);

            TextView debugTextView = new TextView(activity);
            debugTextView.setBackgroundResource(R.color.debug_text_background);
            debugTextView.setTextColor(ContextCompat.getColor
                    (activity, R.color.debug_text_variable_name_color));
            debugTextView.setText(debugName);
            debugTextView.setLayoutParams(params);
            debugTextView.setPadding(padding, 0, 0, 0);
            debugTextView.setTextSize(10.0f);
            debugStringLayout.addView(debugTextView);

            if (debugValue != null) {
                TextView debugValueView = new TextView(activity);
                debugValueView.setBackgroundResource(R.color.debug_text_background);
                debugValueView.setTextColor(ContextCompat.getColor
                        (activity, R.color.debug_text_variable_value_color));
                debugValueView.setText(debugValue);
                debugValueView.setLayoutParams(params);
                debugValueView.setTextSize(10.0f);
                debugStringLayout.addView(debugValueView);
            }

            view.addView(debugStringLayout);
        }


        public abstract void renderElements();
    }


}
