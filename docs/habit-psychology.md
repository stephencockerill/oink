# Psychology-driven design principles

Oink is a workout habit tracker that uses behavioral economics and gamification psychology to help users build consistent exercise habits.
Every product/UX decision should answer: *how does this use psychology to help users build a lasting exercise habit?*

## Core psychological foundation

### Loss aversion (Kahneman & Tversky)
- Losses loom larger than gains - the pain of losing is ~2x the pleasure of gaining.
- In Oink: the halving penalty (÷2) creates stronger motivation to maintain streaks than the +$5 reward alone.
- Implication: the visual and emotional weight of losing should feel significant but not devastating.

### Positive reinforcement over punishment
- Apps using only positive reinforcement (like Finch) prevent the guilt spirals that cause abandonment.
- In Oink: balance the penalty with celebration, progress visualization, and recovery narratives.
- Implication: when the user misses, show the loss but immediately frame the next workout as a "comeback opportunity".

### Recovery mechanisms
- Users with clear recovery paths are far more likely to maintain long-term habits.
- In Oink: streak freezes, insurance options, comeback bonuses.
- Implication: never make the user feel like one miss destroys everything.

### Visual progress & streaks
- Making progress highly visible increases commitment.
- In Oink: the pig character visually evolves (stronger, changed appearance) as balance grows.
- Implication: use animations, progressive visual changes, and prominent streak displays.

### Character attachment
- Pet-care metaphors (like Finch's bird) create emotional investment that drives consistency.
- In Oink: the pig is a companion whose wellbeing reflects the user's habits, not just a mascot.
- Implication: give the pig personality, expressions, and reactions to user actions.

## Design guidelines for every feature

When implementing any feature, ask:

1. **Does this leverage loss aversion appropriately?** Show what the user stands to lose (without being cruel); make gains celebratory and losses recoverable.
2. **Does this maintain positive reinforcement?** Avoid shame/guilt/harsh language; frame misses as temporary setbacks; celebrate small wins.
3. **Does this make progress visible?** Animations, visual changes, numbers; show both short-term (today) and long-term (total balance, streak); make the pig's state reflect current status.
4. **Does this build character attachment?** Give the pig personality; make interactions feel personal; create moments of delight.
5. **Does this support recovery?** Provide paths back after misses; don't punish too harshly (halving is already significant); show that coming back is always possible.

## UI/UX principles

### Tone
- **Encouraging, not preachy**: "Ready to earn another $5?" not "You should exercise today".
- **Playful, not childish**: gym humor and fitness puns, but respect the user's intelligence.
- **Supportive, not guilt-tripping**: "Comeback time!" not "You missed yesterday".

### Visual hierarchy
1. Most prominent: current balance (the user's "score").
2. Second: today's check-in status (did they exercise yet?).
3. Third: streak counter.
4. Supporting: pig character state, history, settings.

### Animations & feedback
- Instant feedback: every action gets an immediate visual/haptic response.
- Celebratory moments: coins dropping, pig jumping, confetti on milestones.
- Empathetic losses: the pig looks disappointed but not destroyed when the balance halves.

### Accessibility
- High contrast for balance numbers; clear, large tap targets; haptic feedback for important actions; support reduced motion.

## Anti-patterns to avoid

- ❌ Make streaks the *only* metric (break it and users abandon). ✅ Show streak but also cumulative earnings, total workouts, personal records.
- ❌ Shame users for missing ("You failed again"). ✅ Encourage recovery ("One workout brings you back on track!").
- ❌ Make the pig "die" or disappear. ✅ Make the pig sad/smaller but always present and hopeful.
- ❌ Add too many features at once (cognitive overload). ✅ Start simple, add depth gradually.
- ❌ Ignore the halving mechanic's psychological weight. ✅ Make halving feel significant through animation/sound, but always show the immediate path forward.

## Milestone system

Design milestones that create sub-goals and variety.

**Financial**: $25 "Quarter Pounder" · $50 "Half-Swole" · $100 "Century Club" · $200 "Swole Savings".
**Streak**: 7d "Week Warrior" · 14d "Fortnight Fighter" · 30d "Monthly Muscle" · 60d "Habit Hero".
**Visual rewards**: unlock pig outfits/accessories, new background environments, special animations, new exercise types the pig performs.

## References for deep dives

- Loss aversion: Kahneman & Tversky (1979), "Prospect Theory".
- Gamification: Duolingo case studies (streak-driven engagement).
- Habit formation: recovery mechanisms materially improve long-term success.
- Pet-care apps: Finch - positive reinforcement without punishment.
