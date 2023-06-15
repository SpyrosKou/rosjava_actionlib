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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ros.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

/**
 * Created at 2020-04-19
 *
 * @author Spyros Koukas
 */
final class NodePrinter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    static final String translateConnectedNodeToString(final Node node) {
        final String result;
        if (node == null) {
            result = null;
        } else {//Start else
            final StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(node.getClass().getCanonicalName() + "{ ");
            {
                final String hashCode = supplierToString(node::hashCode);
                stringBuilder.append("[hashCode:");
                stringBuilder.append(hashCode);
            }
            {
                final String masterUri = supplierToString(node::getMasterUri);
                stringBuilder.append("]\n[masterUri:");
                stringBuilder.append(masterUri);
            }
            {
                final String name = supplierToString(node::getName);
                stringBuilder.append("]\n[name:");
                stringBuilder.append(name);
            }
            {
                stringBuilder.append("]\n[uri:");
                stringBuilder.append(supplierToString(node::getUri));
            }
            stringBuilder.append("}");
            result = stringBuilder.toString();

        }//end else
        return result;
    }

    /**
     * @param stringSupplier
     *
     * @return
     */
    private static String supplierToString(final Supplier<?> stringSupplier) {
        String result = null;
        try {
            result = "" + stringSupplier.get();
        } catch (final Exception e) {
            LOGGER.debug(ExceptionUtils.getStackTrace(e));
        }
        return result;
    }
}
