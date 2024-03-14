package com.github.rosjava_actionlib;

import com.google.common.util.concurrent.Runnables;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Simple Fibonacci Calculator
 *
 * @author Spyros Koukas
 */
final class FibonacciCalculator {
    final int[] fibonacciSequence(int order) {
        return this.fibonacciSequence(order, () -> Boolean.FALSE, Runnables::doNothing);
    }

    final int[] fibonacciSequence(int order, final Supplier<Boolean> shouldCancel, final Runnable onCancel) {
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
            }
        }

        return fibonacciList.stream().mapToInt(Integer::intValue).toArray();
    }
}
