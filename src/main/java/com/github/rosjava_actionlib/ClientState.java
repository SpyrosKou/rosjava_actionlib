package com.github.rosjava_actionlib;
/**
 * Copyright 2020 Spyros Koukas
 * Copyright 2015 Ekumen www.ekumenlabs.com
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

import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The client states
 */
public enum ClientState {
    NO_GOAL(-3),
    INVALID_TRANSITION(-2),
    NO_TRANSITION(-1),
    WAITING_FOR_GOAL_ACK(0),
    PENDING(1),
    ACTIVE(2),
    WAITING_FOR_RESULT(3),
    WAITING_FOR_CANCEL_ACK(4),
    RECALLING(5),
    PREEMPTING(6),
    DONE(7),
    LOST(8),
    UNKNOWN_STATE(99);

    private final int value;

    public Integer getValue() {
        return this.value;
    }

    public final boolean isRunning() {
        return this.getValue() >= ClientState.WAITING_FOR_GOAL_ACK.getValue() && this.getValue() < ClientState.DONE.getValue();

    }

    /**
     * @param value
     */
    ClientState(final int value) {
        this.value = value;
    }

    // Mapping states to state id
    private static final ConcurrentMap<Integer, ClientState> stateIdToClientStateMap = getMapping();

    /**
     * @return
     */
    private static final ConcurrentMap<Integer, ClientState> getMapping() {
        return Arrays.stream(ClientState.values()).collect(Collectors.toConcurrentMap(ClientState::getValue, Function.identity()));
    }

    /**
     * @param state
     * @return
     */
    public final boolean isAmong(final ClientState... state) {
        boolean result = false;
        if (state != null) {
            result = Arrays.stream(state).filter(this::equals).findAny().isPresent();
        }
        return result;
    }

    /**
     * Get state from value
     *
     * @param value value
     * @return state
     */
    public static ClientState from(final int value) {
        return stateIdToClientStateMap.getOrDefault(value, UNKNOWN_STATE);
    }
}
