/**
 * Copyright 2019 Spyros Koukas
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

import org.junit.Assert;
import org.junit.Test;

public class ActionLibMessagesUtilsTest {

    public static final class SampleMessage {
        private String name;
        private int count;

        public final String getName() {
            return this.name;
        }

        public final void setName(final String name) {
            this.name = name;
        }

        public final int getCount() {
            return this.count;
        }

        public final void setCount(final int count) {
            this.count = count;
        }
    }

    @Test
    public final void getterLookupReturnsSubmessage() {
        final SampleMessage message = new SampleMessage();
        message.setName("goal");

        final String result = ActionLibMessagesUtils.getSubMessageFromMessage(message, "getName");

        Assert.assertEquals("goal", result);
    }

    @Test
    public final void setterLookupHandlesReferenceTypes() {
        final SampleMessage message = new SampleMessage();

        ActionLibMessagesUtils.setSubMessageFromMessage(message, "result", "setName");

        Assert.assertEquals("result", message.getName());
    }

    @Test
    public final void setterLookupHandlesPrimitiveParameters() {
        final SampleMessage message = new SampleMessage();

        ActionLibMessagesUtils.setSubMessageFromMessage(message, Integer.valueOf(7), "setCount");

        Assert.assertEquals(7, message.getCount());
    }
}
