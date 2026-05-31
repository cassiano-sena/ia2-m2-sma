---
name: gui-sketch-overview
description: Overview of the 3 desktop GUI sketch variants for the SMA generator rental system
metadata:
  type: sketch
  created: 2026-05-31
  project: ia2-m2-sma
---

# Sketch Overview: Desktop GUI for Multi-Agent Generator Rental System

## Problem
The JADE multi-agent simulation runs entirely in the console. A desktop GUI is needed to visualize agent interactions, message flow, and system state in real-time.

## Agent Map
- **ConsumerAgent** (4x): sends rental requests with load/duration/budget
- **RentalAgent** (1x): validates requests, calculates prices, coordinates transport
- **TransportAgent** (3x): proposes vehicle offers based on load, executes transport

## Sketch Variants

| # | Name | Style | Best For |
|---|------|-------|----------|
| 1 | **Agent Traffic Control** | Dark dashboard, real-time message flow lines | Observing simulation dynamics |
| 2 | **Split Panel** | Left=agent tree, Right=log timeline | Debugging and analysis |
| 3 | **Card Grid** | Consumer cards + Transport cards + central Rental panel | Clean overview, manual trigger |

## Common UI Elements
- Agent status indicators (available/busy)
- Message timeline/log panel
- Real-time price calculation display
- Transport simulation progress bars