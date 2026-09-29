# Animation Library

Animation clips are reusable first-class assets.

## Clip Types

- Locomotion
- Acting
- Facial expression
- Gesture
- Combat/action
- Dance
- Reaction
- Cinematic
- Camera
- Procedural
- Physics-inspired

## Clip Workflow

Preview -> Add to timeline -> Retarget when supported -> Edit -> Save as new clip

## Retargeting

Where compatible rigs exist, a clip can be mapped from one character rig to another through a retargeting layer. Original source data remains unchanged.

## Layered Animation

The runtime should support combining compatible clips, for example:

Walk + Wave + Facial Expression

Animation layers must have controllable blend weights and explicit conflict rules.
