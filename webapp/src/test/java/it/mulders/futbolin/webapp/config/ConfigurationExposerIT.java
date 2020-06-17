package it.mulders.futbolin.webapp.config;

import org.assertj.core.api.WithAssertions;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.jboss.weld.exceptions.WeldException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Named;

class ConfigurationExposerIT implements WithAssertions {
    private static WeldContainer container;
    private static Dummy dummy;

    @Named
    static class Dummy {
        @Inject
        @Config("an.example")
        private String existingString;

        @Inject
        @Config("an.example")
        private int existingInteger;

        @Inject
        @Config("another.example")
        private String nonExisting;

        @Inject
        @Config("an.invalid.number")
        private Integer notNumeric;
    }

    @BeforeAll
    public static void setup() {
        System.setProperty(ConfigurationExposer.CONFIG_PATH_PROPERTY, "src/test/resources/application.properties");
        var weld = new Weld()
                .disableDiscovery()
                .addBeanClass(Dummy.class)
                .addBeanClass(ConfigurationExposer.class);
        container = weld.initialize();
        dummy = container.select(Dummy.class).get();
    }

    @AfterAll
    public static void close() {
        System.clearProperty(ConfigurationExposer.CONFIG_PATH_PROPERTY);
        container.shutdown();
    }

    @Test
    void when_config_path_not_existing_should_fail() {
        var throwable = catchThrowable(() -> {
            System.setProperty(ConfigurationExposer.CONFIG_PATH_PROPERTY, "foo");
            var weld = new Weld()
                    .disableDiscovery()
                    .addBeanClass(Dummy.class)
                    .addBeanClass(ConfigurationExposer.class);
            var dummy = weld.initialize().select(Dummy.class).get();
        });
        assertThat(throwable)
                .isInstanceOf(WeldException.class)
                .hasMessageContaining("WELD-000049");
        //The cause of the WeldException is an InvocationTargetException, whose cause is the Exception our code throws.
        var cause = throwable.getCause().getCause();
        assertThat(cause)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("foo could not be read");
    }

    @Test
    void when_config_path_not_specified_should_fail() {
        var throwable = catchThrowable(() -> {
            System.clearProperty(ConfigurationExposer.CONFIG_PATH_PROPERTY);
            var weld = new Weld()
                    .disableDiscovery()
                    .addBeanClass(Dummy.class)
                    .addBeanClass(ConfigurationExposer.class);
            var dummy = weld.initialize().select(Dummy.class).get();
        });
        assertThat(throwable)
                .isInstanceOf(WeldException.class)
                .hasMessageContaining("WELD-000049");
        //The cause of the WeldException is an InvocationTargetException, whose cause is the Exception our code throws.
        var cause = throwable.getCause().getCause();
        assertThat(cause)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no configuration file given");
    }

    @Test
    void should_inject_string_value_from_property_file() {
        assertThat(dummy.existingString).isEqualTo("1");
    }

    @Test
    void should_inject_integer_value_from_property_file() {
        assertThat(dummy.existingInteger).isEqualTo(1);
    }

    @Test
    void should_not_inject_non_existing_values_from_property_file() {
        assertThat(dummy.nonExisting).isNull();
    }

    @Test
    void should_not_inject_non_numberic_value() {
        assertThat(dummy.notNumeric).isNull();
    }
}