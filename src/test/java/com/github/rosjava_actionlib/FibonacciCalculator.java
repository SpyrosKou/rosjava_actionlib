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

import com.google.common.util.concurrent.Runnables;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Simple Fibonacci Calculator
 *
 * @author Spyros Koukas
 */
final class FibonacciCalculator {
    final int[] fibonacciSequence(final int order) {
        return this.fibonacciSequence(order, () -> Boolean.FALSE, Runnables::doNothing,x->Runnables.doNothing(),Integer.MAX_VALUE);
    }

    final int[] fibonacciSequence(final int order,final Consumer<List<Integer>> updater,final int updateInterval) {
        return this.fibonacciSequence(order, () -> Boolean.FALSE, Runnables::doNothing,updater,updateInterval);
    }
    final int[] fibonacciSequence(final int order,final Supplier<Boolean> shouldCancel, final Runnable onCancel) {
        return this.fibonacciSequence(order, shouldCancel, onCancel,x->Runnables.doNothing(),Integer.MAX_VALUE);
    }

    final int[] fibonacciSequence(int order, final Supplier<Boolean> shouldCancel, final Runnable onCancel, final Consumer<List<Integer>> updater, final int updateInterval) {
        Objects.requireNonNull(updater);

        final ArrayList<Integer> fibonacciList = new ArrayList<>();

        fibonacciList.add(0);
        fibonacciList.add(1);


        for (int i = 2; i < (order + 2); i++) {
            if (shouldCancel.get()) {
                onCancel.run();
                break;
            } else {
                final Integer iMinus1 = fibonacciList.get(i - 1);
                final Integer iMinus2 = fibonacciList.get(i - 2);
                fibonacciList.add(iMinus1 + iMinus2);
                if(i%updateInterval==0){
                    updater.accept(new ArrayList<>(fibonacciList));
                }
            }
        }

        return fibonacciList.stream().mapToInt(Integer::intValue).toArray();
    }
}
