/**
 * Copyright 2024 Spyros Koukas
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

interface TestInputs {

    int TEST_INPUT = 5;
    int[] TEST_CORRECT_OUTPUT = new FibonacciCalculator().fibonacciSequence(TEST_INPUT);
    int HUGE_INPUT = 9999999;
    int[] TEST_CORRECT_HUGE_INPUT_OUTPUT = new FibonacciCalculator().fibonacciSequence(HUGE_INPUT);
}
