/**
 * Copyright 2020 Spyros Koukas
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rosjava_actionlib;


import actionlib_msgs.GoalStatus;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created at 2020-04-19
 *
 * @author Spyros Koukas
 */
final class ActionLibMessagesUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static final String UNKNOWN_GOAL_STATUS = "UNKNOWN GOAL STATUS";
    private static final ConcurrentHashMap<MethodCacheKey, Method> GETTER_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MethodCacheKey, Method> SETTER_METHOD_CACHE = new ConcurrentHashMap<>();

    private record MethodCacheKey(Class<?> ownerClass, String methodName, Class<?> argumentClass) {
    }


    /**
     * Return true if the goalStatus corresponds to one of the 9 known states as presented in {@link GoalStatus}
     *
     * @param goalStatus
     *
     * @return
     */
    public static final boolean isKnownGoalStatus(final byte goalStatus) {
        final boolean result = goalStatus == GoalStatus.PENDING ||
                goalStatus == GoalStatus.ACTIVE ||
                goalStatus == GoalStatus.PREEMPTED ||
                goalStatus == GoalStatus.SUCCEEDED ||
                goalStatus == GoalStatus.ABORTED ||
                goalStatus == GoalStatus.REJECTED ||
                goalStatus == GoalStatus.PREEMPTING ||
                goalStatus == GoalStatus.RECALLING ||
                goalStatus == GoalStatus.RECALLED ||
                goalStatus == GoalStatus.LOST;

        return result;
    }

    /**
     * will return null if goalStatus is null otherwise will call  {@link ActionLibMessagesUtils#goalStatusToString(byte)} on  {@link GoalStatus#getStatus()}
     *
     * @param goalStatus
     *
     * @return
     */
    public static final String goalStatusToString(final GoalStatus goalStatus) {
        if (goalStatus != null) {
            final StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{");
            stringBuilder.append(goalStatus);
            stringBuilder.append(",");
            stringBuilder.append("GoalId:");
            stringBuilder.append(goalStatus.getGoalId());
            stringBuilder.append(",");
            stringBuilder.append("Text:");
            stringBuilder.append(goalStatus.getText());
            stringBuilder.append(",");
            stringBuilder.append("Status:");
            stringBuilder.append(goalStatusToString(goalStatus.getStatus()));
            stringBuilder.append("(");
            stringBuilder.append(goalStatus.getStatus());
            stringBuilder.append(")");
            stringBuilder.append("}");
            return stringBuilder.toString();
        } else {
            return null;
        }
    }

    /**
     *
     */
    private static final String[] GOAL_STATUS_TO_STRING = createGoalStatusArray();

    /**
     *
     * @return
     */
    private static final String[] createGoalStatusArray() {
        final String[] goalStatusArray = new String[10];
        goalStatusArray[GoalStatus.PENDING] = "PENDING";
        goalStatusArray[GoalStatus.ACTIVE] = "ACTIVE";
        goalStatusArray[GoalStatus.PREEMPTED] = "PREEMPTED";
        goalStatusArray[GoalStatus.SUCCEEDED] = "SUCCEEDED";
        goalStatusArray[GoalStatus.ABORTED] = "ABORTED";
        goalStatusArray[GoalStatus.REJECTED] = "REJECTED";
        goalStatusArray[GoalStatus.PREEMPTING] = "PREEMPTING";
        goalStatusArray[GoalStatus.RECALLING] = "RECALLING";
        goalStatusArray[GoalStatus.RECALLED] = "RECALLED";
        goalStatusArray[GoalStatus.LOST] = "LOST";


        return goalStatusArray;
    }

    /**
     * Get a textual representation of {@link GoalStatus}
     *
     * @param goalStatus
     *
     * @return
     */
    public static final String goalStatusToString(final byte goalStatus) {

        final String stateName = (goalStatus >= 0 && goalStatus < GOAL_STATUS_TO_STRING.length) ? GOAL_STATUS_TO_STRING[goalStatus] : UNKNOWN_GOAL_STATUS;


        return stateName;
    }



    /**
     * Return the submessage of class R_SUB_MESSAGE from the message T_MESSAGE by using the getter with the provided name in order to workaround known bug.
     * Workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
     *
     * @param message
     * @param getterMethodName
     * @param <R_SUB_MESSAGE>
     * @param <T_MESSAGE>
     *
     * @return
     */
    static final <R_SUB_MESSAGE, T_MESSAGE> R_SUB_MESSAGE getSubMessageFromMessage(final T_MESSAGE message, final String getterMethodName) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(getterMethodName);
        R_SUB_MESSAGE subMessage = null;

        try {
            final Method m = getCachedGetter(message.getClass(), getterMethodName);
            subMessage = (R_SUB_MESSAGE) m.invoke(message);
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }
        return subMessage;
    }

    /**
     * Set the submessage of class R_SUB_MESSAGE in the message T_MESSAGE by using the setter with the provided name in order to workaround known bug.
     * Workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
     *
     * @param message
     * @param setterMethodName
     * @param <R_SUB_MESSAGE>
     * @param <T_MESSAGE>
     */
    static final <R_SUB_MESSAGE, T_MESSAGE> void setSubMessageFromMessage(final T_MESSAGE message, final R_SUB_MESSAGE submessage, final String setterMethodName) {
        Objects.requireNonNull(message);
        Objects.requireNonNull(setterMethodName);
        try {
            final Method m = getCachedSetter(message.getClass(), setterMethodName, submessage);
            m.invoke(message, submessage);
        } catch (final Exception e) {
            LOGGER.error(ExceptionUtils.getStackTrace(e));
        }


    }

    private static final Method getCachedGetter(final Class<?> messageClass, final String getterMethodName) throws NoSuchMethodException {
        final MethodCacheKey cacheKey = new MethodCacheKey(messageClass, getterMethodName, Void.class);
        final Method cachedMethod = GETTER_METHOD_CACHE.get(cacheKey);
        if (cachedMethod != null) {
            return cachedMethod;
        }

        final Method method = messageClass.getMethod(getterMethodName);
        method.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
        final Method existingMethod = GETTER_METHOD_CACHE.putIfAbsent(cacheKey, method);
        return existingMethod == null ? method : existingMethod;
    }

    private static final Method getCachedSetter(final Class<?> messageClass, final String setterMethodName, final Object submessage) throws NoSuchMethodException {
        final Class<?> argumentClass = submessage == null ? Void.class : submessage.getClass();
        final MethodCacheKey cacheKey = new MethodCacheKey(messageClass, setterMethodName, argumentClass);
        final Method cachedMethod = SETTER_METHOD_CACHE.get(cacheKey);
        if (cachedMethod != null) {
            return cachedMethod;
        }

        final Method method = findCompatibleSetter(messageClass, setterMethodName, argumentClass);
        method.setAccessible(true); // workaround for known bug http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6924232
        final Method existingMethod = SETTER_METHOD_CACHE.putIfAbsent(cacheKey, method);
        return existingMethod == null ? method : existingMethod;
    }

    private static final Method findCompatibleSetter(final Class<?> messageClass, final String setterMethodName, final Class<?> argumentClass) throws NoSuchMethodException {
        Method compatibleMethod = null;
        for (final Method method : messageClass.getMethods()) {
            if (!setterMethodName.equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            final Class<?> parameterType = method.getParameterTypes()[0];
            if (isParameterCompatible(parameterType, argumentClass)) {
                if (parameterType.equals(argumentClass) || wrapPrimitiveType(parameterType).equals(argumentClass)) {
                    return method;
                }
                if (compatibleMethod == null) {
                    compatibleMethod = method;
                }
            }
        }
        if (compatibleMethod != null) {
            return compatibleMethod;
        }
        throw new NoSuchMethodException(messageClass.getName() + "." + setterMethodName + "(" + argumentClass.getName() + ")");
    }

    private static final boolean isParameterCompatible(final Class<?> parameterType, final Class<?> argumentClass) {
        if (argumentClass == Void.class) {
            return !parameterType.isPrimitive();
        }
        return parameterType.isAssignableFrom(argumentClass) || wrapPrimitiveType(parameterType).isAssignableFrom(argumentClass);
    }

    private static final Class<?> wrapPrimitiveType(final Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        return type;
    }
}




