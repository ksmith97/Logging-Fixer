package com.phrozenspectrum;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class MultilineTest {

    @Test
    public void singleLineTest() {
        String initial = "/* test */";
        String result = App.multiline.matcher(initial).replaceAll("");
        assertThat(result).isEqualTo("");
    }

    @Test
    public void multiLineTest() {
        String initial = "/*\n test\n  \n*/abc";
        String result = App.multiline.matcher(initial).replaceAll("");
        assertThat(result).isEqualTo("abc");
    }

    @Test
    public void moreMultiLineTest() {
        String initial = "/*\n * This is a test\n  \n*/\npublic class {\nasd\n/* asdasda*/\n}";
        String result = App.multiline.matcher(initial).replaceAll("");
        assertThat(result).isEqualTo("\npublic class {\nasd\n\n}");
    }
}
