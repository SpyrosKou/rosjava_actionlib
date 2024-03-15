package com.github.rosjava_actionlib;

interface TestInputs {

    int TEST_INPUT = 5;
    int[] TEST_CORRECT_OUTPUT = new FibonacciCalculator().fibonacciSequence(TEST_INPUT);
    int HUGE_INPUT = 9999999;
    int[] TEST_CORRECT_HUGE_INPUT_OUTPUT = new FibonacciCalculator().fibonacciSequence(HUGE_INPUT);
}
