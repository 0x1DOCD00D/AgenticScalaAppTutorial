---
description: Investigate a production problem end to end using the incident-responder agent
---

Production issue reported: $ARGUMENTS

Use the **incident-responder** agent to run the full triage procedure (ECS service state ->
application logs -> database -> ALB metrics), form an evidence-backed hypothesis, take any
remediation inside its rules of engagement, and write the incident report under
`docs/incidents/`. If it must escalate, present the dossier and the specific human decision
needed.
