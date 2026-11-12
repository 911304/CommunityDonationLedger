
package com.community;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebFilter("/*")
public class AuthFilter implements Filter {

    @Override
    public void doFilter(
            javax.servlet.ServletRequest servletRequest,
            javax.servlet.ServletResponse servletResponse,
            FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request =
                (HttpServletRequest) servletRequest;

        HttpServletResponse response =
                (HttpServletResponse) servletResponse;

        response.setHeader("Cache-Control", "no-store");

        String contextPath = request.getContextPath();

        String uri = request.getRequestURI();

        String path = uri.substring(contextPath.length());

        // Public pages and authentication endpoints.
        if (path.equals("/login.html")
                || path.equals("/setup.html")
                || path.equals("/api/login")
                || path.equals("/api/setup")
                || path.equals("/api/setup-status")
                || path.equals("/api/")
                || path.equals("/api")) {

            chain.doFilter(request, response);
            return;
        }

        // Check whether a valid session exists.
        HttpSession session = request.getSession(false);

        boolean loggedIn = session != null
                && session.getAttribute("userId") != null;

        if (loggedIn) {
            chain.doFilter(request, response);
            return;
        }

        // API requests receive JSON instead of an HTML redirect.
        if (path.startsWith("/api")) {

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            response.getWriter().write(
                    "{\"error\":\"Please log in first.\"}"
            );

        } else {
            response.sendRedirect(contextPath + "/login.html");
        }
    }
}
