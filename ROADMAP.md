# 🐷 Oink Roadmap

This roadmap tracks features planned for Oink, organized by psychological impact and implementation complexity.

---

## ✅ Completed Features

### Core Mechanics
- [x] Balance tracking (+$5 for exercise, ÷2 for miss)
- [x] One check-in per day enforcement
- [x] Streak tracking
- [x] Preview gain/loss before check-in
- [x] Retroactive check-ins via calendar

### Recovery Mechanisms
- [x] Streak freezes (buy for $10, max 2)
- [x] Halving penalty (significant but recoverable)
- [x] Missed day freeze prompt

### Widget
- [x] Home screen widget (3x1)
- [x] Balance display
- [x] Streak display with escalating intensity (🔥→🔥🔥→💥🔥💥→👑🔥👑)
- [x] Time-based urgency (background color shifts if not logged)
- [x] Auto-update on check-in

### Notifications
- [x] Daily reminder at configurable time
- [x] Notification channel setup

### Rewards / Cash Out System
- [x] Cash out from piggy bank (treat yourself!)
- [x] Celebratory cash-out flow with animation
- [x] Reward history tracking
- [x] Stats (total earned, workout count)
- [x] Emoji picker for rewards
- [x] "You EARNED this!" framing

### Data & Navigation
- [x] History view (all check-ins)
- [x] Calendar view (visual month overview)
- [x] Settings screen
- [x] Rewards screen
- [x] Room database persistence
- [x] DataStore for preferences

---

## 🚧 In Progress

*Nothing currently in progress*

---

## 📋 Planned Features

### HIGH PRIORITY — Core Psychological Hooks

These features have the highest psychological impact and should be built first.

#### 🐷 Visual Pig Character
**Impact**: Character attachment creates emotional investment (like Finch's bird)

- [ ] **Pig component** - Animated pig that appears on home screen
- [ ] **Pig states based on balance**:
  - `$0-$24`: Tiny piglet (just starting)
  - `$25-$49`: Small pig
  - `$50-$99`: Medium pig
  - `$100-$199`: Buff pig 💪
  - `$200+`: SWOLE pig with muscles
- [ ] **Pig expressions**:
  - Happy/celebrating when you exercise
  - Sad/worried when you miss
  - Excited when approaching milestones
  - Sleeping at night / energetic in morning
- [ ] **Pig animations**:
  - Bounce/jump on successful check-in
  - Deflate animation on miss (but stay hopeful!)
  - Exercise animations (push-ups, jumping jacks)
  - Idle breathing/movement

#### 🎉 Celebration Animations
**Impact**: Instant feedback creates dopamine hits that reinforce behavior

- [ ] **Check-in success animation**:
  - Coins flying into piggy bank
  - Confetti burst
  - "+$5.00" floating text
  - Haptic feedback
- [ ] **Streak milestone celebration**:
  - Special animation at 7, 14, 30, 60 days
  - Badge unlock animation
- [ ] **Balance milestone celebration**:
  - Special animation at $25, $50, $100, $200
  - Achievement unlock animation

#### 🏆 Milestone System
**Impact**: Sub-goals create variety and motivation beyond just the streak

- [ ] **Financial milestones**:
  - 💰 $25 — "Quarter Pounder"
  - 💰 $50 — "Half-Swole"
  - 💰 $100 — "Century Club"
  - 💰 $200 — "Swole Savings"
  - 💰 $500 — "High Roller"
  - 💰 $1000 — "Piggy Bank Millionaire" (okay, thousandaire)

- [ ] **Streak milestones**:
  - 🔥 7 days — "Week Warrior"
  - 🔥 14 days — "Fortnight Fighter"
  - 🔥 30 days — "Monthly Muscle"
  - 🔥 60 days — "Habit Hero"
  - 🔥 100 days — "Century Centurion"
  - 🔥 365 days — "Year of the Pig"

- [ ] **Achievement storage** (Room database)
- [ ] **Achievement display screen**
- [ ] **Achievement badges on home screen**

---

### MEDIUM PRIORITY — Enhanced Engagement

#### 📊 Statistics & Insights
- [ ] Total workouts all-time
- [ ] Best streak ever
- [ ] Highest balance ever
- [ ] Workout frequency (workouts per week average)
- [ ] Recovery rate (how often you bounce back after miss)
- [ ] Graph of balance over time

#### 🔊 Sound Effects
- [ ] Coin drop sound on exercise check-in
- [ ] Sad trombone on miss (gentle, not harsh)
- [ ] Achievement unlock fanfare
- [ ] Optional (respect user preference)

#### 🎨 Pig Customization
- [ ] Unlock pig accessories at milestones:
  - Headband (7-day streak)
  - Sweatband (14-day streak)
  - Gym shorts ($50 balance)
  - Dumbbells ($100 balance)
  - Cape ($200 balance)
- [ ] Customization screen to equip unlocked items

#### 🌙 Time-of-Day Theming
- [ ] Morning: Energetic pig, bright colors, "Rise and grind!"
- [ ] Afternoon: Active pig, warm colors
- [ ] Evening: Calm pig, softer colors, "Last chance today!"
- [ ] Night: Sleepy pig, dark mode emphasis

---

### LOW PRIORITY — Nice-to-Haves

#### 📤 Social Features
- [ ] Share achievements to social media
- [ ] Share streak milestones
- [ ] Generate shareable "Oink card" image

#### 📈 Advanced Analytics
- [ ] Weekly/monthly reports
- [ ] Trend analysis
- [ ] Best workout days (day of week analysis)

#### 💸 Spending Tracker (Maybe)
- [ ] Set a "goal" to spend balance on (new gym gear, etc.)
- [ ] Track progress toward spending goal
- [ ] *Note: This might distract from core loop*

#### 🔔 Smart Notifications
- [ ] Different messages based on streak length
- [ ] Urgent notifications if about to lose big balance
- [ ] Celebration notifications for milestones
- [ ] Weekly summary notification

#### ⌚ Wear OS Widget
- [ ] Simple check-in from watch
- [ ] Balance/streak glance

---

## 🐛 Known Issues / Tech Debt

- [ ] Widget might need manual refresh after app update
- [ ] Consider migration strategy for database schema changes
- [ ] Add proper error handling for edge cases
- [ ] Unit tests for ViewModel and Repository
- [ ] UI tests for critical flows

---

## 💡 Ideas to Explore

These are unvalidated ideas that might be worth researching:

- **Workout type tracking**: Different exercises, not just "did/didn't"
- **Intensity levels**: Bonus for hard workouts?
- **Rest day mechanic**: Planned rest days that don't break streak
- **Buddy system**: Accountability partner feature
- **Challenges**: Weekly challenges for bonus rewards
- **Seasonal events**: Holiday-themed pig outfits

---

## Design Principles (Reference)

When implementing ANY feature, ask:

1. **Does it leverage loss aversion appropriately?**
2. **Does it maintain positive reinforcement?** (No guilt/shame)
3. **Does it make progress visible?**
4. **Does it build character attachment?**
5. **Does it support recovery?**

See `.cursor/rules/habit-psychology.mdc` for full guidelines.

---

## Version History

### v1.0.0 (Current)
- Core mechanics complete
- Widget with escalating urgency
- Streak freezes
- Basic UI

### v1.1.0 (Next)
- Visual pig character
- Celebration animations
- Milestone system

---

*Last updated: December 2024*

