# Stage 2 – Implementation (EECS3311)

| Deliverable | Where |
|---|---|
| **Stage 2 report (PDF)** | [`../stage2-report.pdf`](../stage2-report.pdf) (repository root) |
| Demonstration video | <https://youtu.be/qk7mwRH_6Yk> (per-feature timestamps in report Sections 2 and 7) |
| Source code | [`../src/main/java/com/studyagent/`](../src/main/java/com/studyagent/) |
| How to set up and run | [`../README.md`](../README.md) |

## Updated UML diagrams (report Section 3)

| Diagram | File |
|---|---|
| Class diagram, part 1 – MVC, services, repositories (Figure 1) | [`UMLs/class/class-overview-a-mvc.svg`](UMLs/class/class-overview-a-mvc.svg) |
| Class diagram, part 2 – AI subsystem and patterns (Figure 2) | [`UMLs/class/class-overview-b-ai.svg`](UMLs/class/class-overview-b-ai.svg) |
| Complete class diagram, every field and method (Appendix A) | [`UMLs/class/classdiagram-full.svg`](UMLs/class/classdiagram-full.svg) · UMLet source [`classdiagram.uxf`](UMLs/class/classdiagram.uxf) |
| Use-case diagram (Figure 3) | [`UMLs/use-case/usecase-stage2.svg`](UMLs/use-case/usecase-stage2.svg) |
| SD2 – F03 Document Summarization (Figure 4) | [`UMLs/sequence/sd2-summarize.svg`](UMLs/sequence/sd2-summarize.svg) |
| SD3 – F05 Question Answering (Figure 5) | [`UMLs/sequence/sd3-qa.svg`](UMLs/sequence/sd3-qa.svg) |
| SD4 – F08 Quiz Generation, also F06/F07 (Figure 6) | [`UMLs/sequence/sd4-generate.svg`](UMLs/sequence/sd4-generate.svg) |
| SD5 – F09 Grading and F10 Explanation (Figure 7) | [`UMLs/sequence/sd5-grade-explain.svg`](UMLs/sequence/sd5-grade-explain.svg) |
| SD6 – F11 Weak Topics and F12 Adaptive Difficulty (Figure 8) | [`UMLs/sequence/sd6-weak-adaptive.svg`](UMLs/sequence/sd6-weak-adaptive.svg) |

The Stage 1 diagrams are unchanged in [`../Stage1/UMLs/`](../Stage1/UMLs/). SD1 (Course Setup) has no Stage 2 version because F01 was removed.

## Folder layout

```
Stage2/
├── README.md        this index
└── UMLs/
    ├── class/       class diagrams (.svg) + UMLet source (.uxf)
    ├── use-case/    use-case diagram
    └── sequence/    sequence diagrams SD2–SD6
```
