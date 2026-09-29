# Cartoon Studio

Production-grade Android 2D animation studio for cartoons, animated films, motion graphics, and interactive 2D experiences.

## Vision

Idea -> Story -> Characters -> Assets -> Scenes -> Animation -> Audio -> Compositing -> Preview -> Export

## Core Capabilities

- Raster and vector drawing
- Professional timeline and dope sheet
- Keyframes, curves, interpolation and animation clips
- Frame-by-frame animation and onion skinning
- Layers, groups, masks, blend modes and effects
- Character rigs, bones, constraints and reusable poses
- Scenes, shots, cameras and parallax
- Audio tracks, markers and lip-sync foundations
- Asset library and reusable production assets
- Autosave, recovery and versioned project files
- Preview and deterministic offline rendering
- Image sequence, GIF, video and alpha export foundations
- Extension-ready import/export and optional AI integrations

## Architecture

UI -> Feature -> Domain -> Engine -> Data/Platform

Pure animation mathematics and project rules remain independent from Android UI. Preview and export share the same scene evaluation contracts.

## Repository Structure

Cartoon-Studio/
  app/
  build-logic/
  core/ (common, math, time, serialization, undo, diagnostics, testing)
  domain/ (model, animation, drawing, rigging, audio, camera, export)
  engine/ (scene, animation, drawing, rigging, rendering, compositing, audio, export)
  feature/ (project, editor, timeline, drawing, animation, rigging, scenes, assets, audio, compositing, export, settings, onboarding)
  data/ (project-store, asset-store, preferences, cache)
  platform/ (android, graphics, media, filesystem, permissions)
  ui/ (design-system, components, icons, accessibility)
  integrations/ (importers, exporters, ai)
  docs/ (architecture, product, engine, formats, performance, security, roadmap)
  samples/
  scripts/
  gradle/
  .github/

## Delivery Phases

M0 Foundation -> M1 Project Core -> M2 Drawing -> M3 Animation -> M4 Scenes -> M5 Characters -> M6 Audio -> M7 Compositing -> M8 Export -> M9 Performance -> M10 Production Hardening -> M11 Extensions -> M12 Intelligent Tools

## Engineering Principles

1. Modular first
2. Offline first
3. Non-destructive editing
4. Deterministic rendering
5. Performance as a first-class requirement
6. Testable core logic
7. Versioned project schemas
8. Safe recovery and autosave
9. Accessibility
10. Documentation as part of the product

See docs/architecture/ARCHITECTURE.md and docs/roadmap/ROADMAP.md.
