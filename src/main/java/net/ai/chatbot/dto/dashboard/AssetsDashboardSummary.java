package net.ai.chatbot.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Lightweight summary of the user's media library (Supabase-backed
 * {@code media_assets} collection). Buckets MIME types into a small set of
 * categories so the dashboard can render a quick "what's in your asset
 * library" panel without enumerating individual files.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetsDashboardSummary {

    /** Total assets owned by the user. */
    private long total;

    /**
     * Counts grouped by coarse MIME bucket. Keys: image, video, audio,
     * document, other.
     */
    private Map<String, Long> byMimeBucket;

    /** Number of assets uploaded in the trailing 30 days. */
    private long uploads30d;
}
