package com.phrozenspectrum;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 */
public class RegexTest {

    private String regex = "(?m)^(\\s*)System\\.out\\.println";

    @Test
    public void testReplacement() {
        String initial = "asd\n    System.out.println(\"asd\");";
        String result = initial.replaceAll(regex, "$1LOGGER.debug");
        assertThat(result).isEqualTo("asd\n    LOGGER.debug(\"asd\");");

    }

    @Test
    public void testComments() {
        String initial = "asd\n    //System.out.println(\"asd\");";
        String result = initial.replaceAll(regex, "LOGGER.debug");
        assertThat(result).isEqualTo(initial);
    }

        @Test
    public void testComments2() {
        String initial = "asd\n    // System.out.println(\"asd\");";
        String result = initial.replaceAll(regex, "LOGGER.debug");
        assertThat(result).isEqualTo(initial);
    }

    @Test
    public void testComments3() {
        String initial = "asd\n//System.out.println(\"asd\");";
        String result = initial.replaceAll(regex, "LOGGER.debug");
        assertThat(result).isEqualTo(initial);
    }

    @Test
    public void testComments4() {
        String initial = "asd\n// System.out.println(\"asd\");";
        String result = initial.replaceAll(regex, "LOGGER.debug");
        assertThat(result).isEqualTo(initial);
    }

    @Test
    public void testComments5() {
        String initial = "asd\ntest;// System.out.println(\"asd\");";
        String result = initial.replaceAll(regex, "LOGGER.debug");
        assertThat(result).isEqualTo(initial);
    }
}
