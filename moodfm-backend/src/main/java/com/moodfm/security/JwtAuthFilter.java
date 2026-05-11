package com.moodfm.security;

import com.moodfm.common.constant.RedisKeys;
import com.moodfm.common.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            // Parse the JWT once and extract all claims from the single result
            JwtUtil.ParsedToken parsed = jwtUtil.parseAll(token);

            if (!parsed.isAccessToken()) {
                chain.doFilter(request, response);
                return;
            }

            String blacklistKey = RedisKeys.format(RedisKeys.JWT_BLACKLIST, parsed.jti());
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(blacklistKey))) {
                chain.doFilter(request, response);
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(parsed.userId()));

            // Reject soft-deleted or locked users (issue #11)
            if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                chain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities()
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        } catch (Exception e) {
            // UsernameNotFoundException (soft-deleted/disabled account) or other runtime errors
            // — do not set authentication; let Spring Security reject as anonymous
            log.debug("JWT auth failed: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
