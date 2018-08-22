/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.logback.test;

import org.apache.felix.logback.test.helper.LogTestHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

public class Log4j2Test extends LogTestHelper {

    @Test
    public void test() {

        // You'll notice that this setup requires that we publish
        // an slf4j Provider as a service. See the activator in this bundle.

        long time = System.nanoTime();
        Logger logger = LogManager.getLogger(getClass());
        if (logger.isInfoEnabled()) {
            logger.info(time + "");
        }
        assertLog("INFO", getClass().getName(), time);
    }

}
