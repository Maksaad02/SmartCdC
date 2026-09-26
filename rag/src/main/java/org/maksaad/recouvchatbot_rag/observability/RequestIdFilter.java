package org.maksaad.recouvchatbot_rag.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identifiant de correlation d'une requete.
 *
 * Une question du chatbot traverse nginx, le chatbot, le backend puis la base : sans
 * identifiant commun, impossible de relier leurs journaux. L'identifiant est celui recu dans
 * X-Request-Id (pose par nginx) ou, a defaut, genere ici ; il est inscrit dans chaque ligne de
 * journal (MDC "requestId") et renvoye dans la reponse pour que l'utilisateur puisse le citer
 * au support.
 *
 * Une valeur recue n'est retenue que si elle a un format sur : un en-tete hostile ne doit pas
 * pouvoir injecter de faux journaux (retours a la ligne) ni des valeurs demesurees.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString().replace("-", "");
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Les threads du conteneur sont reutilises.
            MDC.remove(MDC_KEY);
        }
    }
}
