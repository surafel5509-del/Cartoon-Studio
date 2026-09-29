# Architecture Decisions

## ADR-001 Modular Architecture

Separate core, domain, engine, feature, data, platform and UI modules to prevent circular dependencies.

## ADR-002 Android-First Engine-Centric Design

Android is the primary product platform while animation and rendering contracts remain platform-independent for testability and future integrations.

## ADR-003 Deterministic Scene Evaluation

Preview and export share scene evaluation contracts so exported frames represent the same animation model.

## ADR-004 Versioned Project Schema

Project data is versioned from the first implementation because creative projects must survive application upgrades.

## ADR-005 Command-Based Editing

Mutating editor operations use commands where practical for undo/redo, recovery and future extensibility.
