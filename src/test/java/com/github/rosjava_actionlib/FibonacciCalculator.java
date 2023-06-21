package com.github.rosjava_actionlib;

/**
 * Simple Fibonacci Calculator
 *
 * @author Spyros Koukas
 */
final class FibonacciCalculator {
    final int[] fibonacciSequence(int order) {
        int i;
        final int[] fib = new int[order + 2];

        fib[0] = 0;
        fib[1] = 1;

        for (i = 2; i < (order + 2); i++) {
            fib[i] = fib[i - 1] + fib[i - 2];
        }
        return fib;
    }
}
