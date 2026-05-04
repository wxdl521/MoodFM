package com.moodfm.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MoodParams {

    private MoodVector mood;

    @JsonProperty("tempo_range")
    private int[] tempoRange;

    @JsonProperty("preferred_genres")
    private List<String> preferredGenres;

    @JsonProperty("preferred_languages")
    private List<String> preferredLanguages;

    @JsonProperty("preferred_eras")
    private List<String> preferredEras;

    @JsonProperty("avoid_keywords")
    private List<String> avoidKeywords;

    @JsonProperty("vibe_keywords")
    private List<String> vibeKeywords;

    @JsonProperty("scene_inferred")
    private String sceneInferred;

    @JsonProperty("energy_curve")
    private String energyCurve;

    @JsonProperty("duration_estimate_minutes")
    private int durationEstimateMinutes = 30;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MoodVector {
        private double valence;
        private double energy;
        private double tension;
    }

    public static MoodParams defaultParams() {
        MoodParams p = new MoodParams();
        MoodVector mv = new MoodVector();
        mv.setValence(0.5);
        mv.setEnergy(0.5);
        mv.setTension(0.3);
        p.setMood(mv);
        p.setTempoRange(new int[]{80, 120});
        p.setPreferredLanguages(List.of("zh", "en"));
        p.setSceneInferred("通用");
        p.setEnergyCurve("flat");
        p.setDurationEstimateMinutes(30);
        return p;
    }
}
