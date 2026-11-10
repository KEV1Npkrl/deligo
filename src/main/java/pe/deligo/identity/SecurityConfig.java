package pe.deligo.identity;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.deligo.shared.Store;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService users(Store db) {
        return email -> {
            var found = db.list("SELECT * FROM app_user WHERE email=?", email.toLowerCase(java.util.Locale.ROOT));
            if (found.isEmpty()) throw new UsernameNotFoundException("Credenciales incorrectas");
            var u = found.getFirst();
            return User.withUsername(Store.str(u,"email")).password(Store.str(u,"password_hash"))
                .roles(Store.str(u,"user_role")).disabled(!Store.yes(u,"active")).build();
        };
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
            .requestMatchers("/", "/index.html", "/assets/**", "/app.js", "/styles.css", "/favicon.svg",
                             "/api/auth/csrf", "/api/auth/me", "/api/auth/register", "/api/catalog/**", "/error").permitAll()
            .anyRequest().authenticated())
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth) -> { res.setContentType("application/json"); res.getWriter().write("{\"ok\":true}"); })
                .failureHandler((req,res,e) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Credenciales incorrectas.\"}"); }))
            .logout(l -> l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,a) -> res.setStatus(204)))
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,x) -> res.sendError(401)));
        return http.build();
    }
}
