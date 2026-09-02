package com.tickify.security;

import com.tickify.user.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the two ways role-based access control on this codebase can silently stop working.
 *
 * <p>Both of these were real defects: {@code @EnableMethodSecurity} was absent, which makes
 * Spring register no method interceptor and turns every {@code @PreAuthorize} on the
 * controllers into a no-op; and the staff controller carried {@code @PreAuthorize("HAS_STAFF")},
 * which is not a valid expression. Neither shows up as a failure at startup — the endpoints
 * simply stop being protected — so they are asserted here rather than trusted.
 */
@DisplayName("Security configuration")
class SecurityConfigurationTest {

    private static final String CONTROLLER_PACKAGE = "com.tickify";

    /** The authority-checking calls a @PreAuthorize on a controller is expected to use. */
    private static final Pattern AUTHORITY_EXPRESSION =
            Pattern.compile(".*\\b(hasRole|hasAnyRole|hasAuthority|hasAnyAuthority|permitAll|isAuthenticated|denyAll)\\b.*");

    @Test
    @DisplayName("method security is enabled, so @PreAuthorize is actually enforced")
    void methodSecurityIsEnabled() {
        assertThat(SecurityConfig.class.getAnnotation(EnableMethodSecurity.class))
                .as("SecurityConfig must be annotated with @EnableMethodSecurity; without it "
                        + "every @PreAuthorize on every controller is silently ignored")
                .isNotNull();
    }

    @Test
    @DisplayName("every @PreAuthorize expression parses and performs an authority check")
    void everyPreAuthorizeExpressionIsValid() {
        SpelExpressionParser parser = new SpelExpressionParser();
        List<String> problems = new ArrayList<>();

        for (Class<?> controller : controllers()) {
            for (PreAuthorize annotation : preAuthorizeAnnotations(controller)) {
                String expression = annotation.value();

                try {
                    parser.parseExpression(expression);
                } catch (ParseException e) {
                    problems.add(controller.getSimpleName() + ": unparseable expression \"" + expression + "\"");
                    continue;
                }

                // "HAS_STAFF" parses as a property reference and evaluates to null, which
                // Spring treats as denied — quiet in tests, and a trap the moment it is
                // "fixed" by someone loosening the rule instead of correcting the expression.
                if (!AUTHORITY_EXPRESSION.matcher(expression).matches()) {
                    problems.add(controller.getSimpleName() + ": \"" + expression
                            + "\" performs no authority check");
                }
            }
        }

        assertThat(problems).as("invalid @PreAuthorize expressions").isEmpty();
    }

    @Test
    @DisplayName("the endpoints that mutate ticketing state all carry an authorization rule")
    void privilegedControllersAreGuarded() {
        Set<String> mustBeGuarded = Set.of(
                "BookingController", "QueueController", "StaffValidateController",
                "EventController", "TicketTypeController");

        List<String> unguarded = controllers().stream()
                .filter(c -> mustBeGuarded.contains(c.getSimpleName()))
                .filter(c -> preAuthorizeAnnotations(c).isEmpty())
                .map(Class::getSimpleName)
                .toList();

        assertThat(unguarded).as("controllers with no @PreAuthorize at all").isEmpty();
    }

    private List<Class<?>> controllers() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> found = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            if (candidate instanceof AnnotatedBeanDefinition definition) {
                try {
                    found.add(Class.forName(definition.getMetadata().getClassName()));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Scanned class disappeared", e);
                }
            }
        }

        assertThat(found).as("controllers discovered under " + CONTROLLER_PACKAGE).isNotEmpty();
        return found;
    }

    private List<PreAuthorize> preAuthorizeAnnotations(Class<?> controller) {
        List<PreAuthorize> annotations = new ArrayList<>();

        PreAuthorize onType = controller.getAnnotation(PreAuthorize.class);
        if (onType != null) {
            annotations.add(onType);
        }
        for (Method method : controller.getDeclaredMethods()) {
            PreAuthorize onMethod = method.getAnnotation(PreAuthorize.class);
            if (onMethod != null) {
                annotations.add(onMethod);
            }
        }
        return annotations;
    }
}
