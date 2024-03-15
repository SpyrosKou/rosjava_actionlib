/**
 * 
 */
package eu.test.utils;
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

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Objects;
import java.util.Properties;

/**
 * @author Spyros Koukas
 *
 */
final class PropertiesUtilities {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private PropertiesUtilities() {
    }


    /**
     * @param propertyName
     * @param properties
     * @return
     */
    public static final int getInt(final String propertyName, final Properties properties) {
        Objects.requireNonNull(properties);
        Preconditions.checkArgument(StringUtils.isNotBlank(propertyName), "propertyName should not be blank.");
        int result = 0;
        final String propertyValue = properties.getProperty(propertyName);
        try {
            result = Integer.parseInt(propertyValue);
        }
        catch (final Exception e) {
            LOGGER.error(propertyName + " defines the " + propertyName + " as:" + propertyValue + " while an integer is expected.");
            throw new IllegalStateException(e);

        }
        return result;
    }

    /**
     * @param propertyName
     * @param properties
     * @return
     */
    public static final long getLong(final String propertyName, final Properties properties) {
        Objects.requireNonNull(properties);
        Preconditions.checkArgument(StringUtils.isNotBlank(propertyName), "propertyName should not be blank.");
        long result = 0;
        final String propertyValue = properties.getProperty(propertyName);
        try {
            result = Long.parseLong(propertyValue);
        }
        catch (final Exception e) {
            LOGGER.error(propertyName + " defines the " + propertyName + " as:" + propertyValue + " while a long is expected.");
            throw new IllegalStateException(e);

        }
        return result;
    }


}
