# Architecture

## Layers

UI -> Feature -> Domain -> Engine -> Data/Platform

UI owns presentation and gestures. Feature modules implement user workflows. Domain contains pure Kotlin models and rules. Engine evaluates scenes, animation, drawing and rendering. Data and Platform provide persistence and Android/device adapters.

## Persistent vs Session State

Persistent project state is serialized. Editor/session state includes selection, panels and temporary interaction state. Render caches are never required to recover a project.

## Command Model

User mutation follows: Intent -> Command -> Validate -> Apply -> Record -> Notify.

Commands provide reliable undo/redo and make complex editor operations composable.

## Rendering Pipeline

Project State -> Scene Evaluation -> Render Graph -> Graphics Backend -> Frame Output

Preview and offline export use the same scene evaluation contracts.

## Concurrency

Autosave, thumbnails, waveform analysis and export are cancellable background jobs with observable progress.

## Recovery

Use autosave snapshots, atomic writes, transaction boundaries, corrupt-project detection and safe read-only fallback.
