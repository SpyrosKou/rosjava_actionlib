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
