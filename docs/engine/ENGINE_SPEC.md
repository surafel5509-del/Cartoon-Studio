# Animation Engine Specification

## Time

Use explicit domain types for FrameRate, Frame, Time, TimeRange, Marker and Duration. Avoid floating-point frame numbers in persistent animation state.

## Animation Primitives

- AnimatableProperty<T>
- Keyframe<T>
- Track<T>
- Interpolation
- AnimationClip
- ClipInstance
- Constraint
- AnimationLayer

## Scene Graph

Scene nodes may represent artwork, groups, cameras, rigs, audio references and nested scene instances.

## Renderer Responsibilities

Evaluate visible scene, resolve transforms, masks and blending, submit draw operations, manage GPU resources, and produce preview or export frames. The renderer does not own editing business rules.
