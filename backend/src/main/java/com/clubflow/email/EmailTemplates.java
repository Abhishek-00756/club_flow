package com.clubflow.email;

import org.springframework.web.util.HtmlUtils;

/** Minimal, client-safe HTML (inline styles only) shared by every notification email. */
public final class EmailTemplates {
    private EmailTemplates() {}

    public static String render(String heading, String message, String actionUrl, String actionLabel) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<!doctype html><html><body style=\"margin:0;padding:0;background:#f3f5f9;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:#f3f5f9;padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:560px;width:100%;background:#ffffff;border:1px solid #d9dee8;border-radius:8px;\">")
                .append("<tr><td style=\"padding:20px 28px;border-bottom:1px solid #e8ebf2;")
                .append("font:700 18px Arial,Helvetica,sans-serif;color:#2545f5;\">ClubFlow</td></tr>")
                .append("<tr><td style=\"padding:28px;font:15px/1.55 Arial,Helvetica,sans-serif;color:#0f1b3d;\">")
                .append("<h1 style=\"margin:0 0 12px;font:700 20px/1.3 Arial,Helvetica,sans-serif;color:#0f1b3d;\">")
                .append(HtmlUtils.htmlEscape(heading)).append("</h1>");
        if (message != null && !message.isBlank()) {
            sb.append("<p style=\"margin:0 0 20px;white-space:pre-line;\">")
                    .append(HtmlUtils.htmlEscape(message)).append("</p>");
        }
        if (actionUrl != null && !actionUrl.isBlank()) {
            sb.append("<a href=\"").append(HtmlUtils.htmlEscape(actionUrl)).append("\" ")
                    .append("style=\"display:inline-block;background:#2545f5;color:#ffffff;text-decoration:none;")
                    .append("padding:10px 18px;border-radius:6px;font-weight:600;\">")
                    .append(HtmlUtils.htmlEscape(actionLabel == null ? "Open in ClubFlow" : actionLabel))
                    .append("</a>");
        }
        sb.append("</td></tr><tr><td style=\"padding:16px 28px;border-top:1px solid #e8ebf2;")
                .append("font:12px Arial,Helvetica,sans-serif;color:#5b6788;\">")
                .append("You get this email because of your ClubFlow notification settings. ")
                .append("You can turn emails off from Settings.</td></tr></table></td></tr></table></body></html>");
        return sb.toString();
    }
}
