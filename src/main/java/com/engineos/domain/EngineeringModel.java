package com.engineos.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EngineeringModel {
    private final UUID id = UUID.randomUUID();
    private final String humanIntent;
    private final List<Investigation> investigations = new ArrayList<>();
    private final List<EngineeringTask> tasks = new ArrayList<>();
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<String> conversation = new ArrayList<>();

    public EngineeringModel(String humanIntent) {
        if (humanIntent == null || humanIntent.isBlank()) {
            throw new IllegalArgumentException("Human engineering intent is required");
        }
        this.humanIntent = humanIntent.trim();
        conversation.add("Human: " + this.humanIntent);
    }

    public UUID id() { return id; }
    public String humanIntent() { return humanIntent; }
    public List<Investigation> investigations() { return List.copyOf(investigations); }
    public List<EngineeringTask> tasks() { return List.copyOf(tasks); }
    public List<Evidence> evidence() { return List.copyOf(evidence); }
    public List<String> conversation() { return List.copyOf(conversation); }

    public void addInvestigation(Investigation investigation) { investigations.add(investigation); }
    public void addTask(EngineeringTask task) { tasks.add(task); }
    public void recordEvidence(Evidence item) { evidence.add(item); }
    public void converse(String speaker, String message) {
        conversation.add(speaker + ": " + message);
    }
}
