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

## 🎨 Art & Design

### Pig Character Art
The pig is the soul of this app. We need actual art, not just emoji placeholders.

**Options to explore:**
- [ ] **Commission an artist** — Find someone on Fiverr, Upwork, or r/HungryArtists
  - Need: Multiple pig states (tiny → swole progression)
  - Need: Different expressions (happy, sad, excited, sleeping)
  - Need: Animations/sprite sheets for movement
  - Budget estimate: $200-500 for full character set

- [ ] **AI-generated art** — Use Midjourney/DALL-E for concepts, then refine
  - Pros: Fast iteration, cheap
  - Cons: May lack consistency, licensing questions

- [ ] **Lottie animations** — Consider [LottieFiles](https://lottiefiles.com/) for pre-made pig animations
  - Pros: Easy to implement, looks great
  - Cons: Less custom, may not match our vision

- [ ] **DIY pixel art** — Retro style could be charming and achievable
  - Tools: Aseprite, Piskel

**Art requirements checklist:**
- [ ] Pig progression states (5+ versions from piglet to swole)
- [ ] Facial expressions (happy, sad, excited, determined, sleeping)
- [ ] Exercise animations (push-ups, jumping, celebrating)
- [ ] Idle animation (breathing, blinking)
- [ ] App icon (pig face, recognizable at small sizes)
- [ ] Widget assets
- [ ] Splash screen

### Visual Style & Theming
Currently using Material 3 defaults. Need to define our actual brand.

- [ ] **Color palette** — What vibe do we want?
  - Playful & bright? (Duolingo-style greens/oranges)
  - Warm & cozy? (Soft pinks, warm neutrals)
  - Bold & energetic? (High contrast, gym vibes)
  - Money-focused? (Greens and golds)

- [ ] **Typography** — Current fonts are generic
  - Consider: Rounded, friendly fonts (Nunito, Poppins, Quicksand)
  - Balance display: Bold, impactful numbers

- [ ] **Overall aesthetic direction**
  - Reference apps: Finch (soft, nurturing), Duolingo (playful), Cash App (bold money vibes)

- [ ] **Dark mode polish** — Make it actually look good, not just inverted

---

## 🚀 Play Store Release

Getting on Google Play is a multi-step process. Here's what we need:

### Pre-Launch Checklist
- [ ] **Google Play Developer account** — $25 one-time fee
  - Sign up at [play.google.com/console](https://play.google.com/console)

- [ ] **App signing & release build**
  - Generate upload key (keep it SAFE - lose it = lose app)
  - Create signed APK/AAB (Android App Bundle preferred)
  - Enable Play App Signing (recommended)

- [ ] **Store listing assets**
  - [ ] App icon (512x512 PNG)
  - [ ] Feature graphic (1024x500 PNG) — the banner image
  - [ ] Screenshots (phone, maybe tablet)
    - At least 2, recommended 4-8
    - Show key features: balance, streak, widget, rewards
  - [ ] Short description (80 chars max)
  - [ ] Full description (4000 chars max)

- [ ] **Content rating questionnaire** — Answer questions about app content

- [ ] **Privacy policy** — Required! Host somewhere (GitHub Pages works)
  - What data we collect (local only? any analytics?)
  - How it's used

- [ ] **Target audience & content** — Confirm not targeted at children under 13

### Technical Requirements
- [ ] **Remove destructive migration** — Add proper Room migrations before release!
- [ ] **Crash reporting** — Consider Firebase Crashlytics
- [ ] **Analytics** (optional) — Firebase Analytics, privacy-respecting
- [ ] **Test on multiple devices** — Different screen sizes, Android versions
- [ ] **ProGuard/R8 optimization** — Shrink release build

### Launch Strategy
- [ ] **Closed testing first** — Internal testing track, then closed beta
- [ ] **Gather feedback** — Fix issues before public launch
- [ ] **Open production** — Go live!

### Post-Launch
- [ ] **Monitor reviews** — Respond to feedback
- [ ] **Crash monitoring** — Fix issues fast
- [ ] **Update regularly** — Shows app is maintained

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

#### 🎉 Animations & Effects
**Impact**: Instant feedback creates dopamine hits that reinforce behavior

##### Check-in Animations
- [ ] **Exercise check-in success**:
  - Coins flying into piggy bank (Lottie animation)
  - Confetti burst
  - "+$5.00" floating text with spring animation
  - Haptic feedback (success pattern)
  - Pig celebration animation

- [ ] **Miss check-in**:
  - Gentle "deflate" animation (not harsh!)
  - Balance reduction shown visually
  - Immediate "You can come back!" messaging
  - Soft haptic (not punishing)

##### Milestone Celebrations
- [ ] **Streak milestones** (7, 14, 30, 60 days):
  - Full-screen celebration overlay
  - Badge unlock animation
  - Shareable achievement card

- [ ] **Balance milestones** ($25, $50, $100, $200):
  - Pig evolution animation
  - "Level up" fanfare
  - New pig state reveal

##### Cash-Out Celebration (Current: basic, needs enhancement)
- [ ] **Enhanced reward animation**:
  - Piggy bank "breaking open" effect
  - Money/coins shower
  - Big celebratory "YOU EARNED IT!" with confetti
  - Pig doing victory dance

##### Implementation Notes
- Consider [Lottie](https://lottiefiles.com/) for complex animations
- Use Compose animation APIs for simpler transitions
- Always include haptic feedback option (can be disabled)
- Respect "reduce motion" accessibility setting

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
- Cash-out / Rewards system
- Basic UI (Material 3 defaults)

### v1.1.0 (Next) — "The Pig Update"
**Goal: Make this app actually feel like OINK, not a generic habit tracker**

- [ ] Pig character art (commissioned or DIY)
- [ ] Basic pig integration on home screen
- [ ] Celebration animations (check-in success)
- [ ] Defined color palette and typography
- [ ] App icon and splash screen

### v1.2.0 — "Polish & Play Store"
- [ ] Full pig animation system
- [ ] Milestone celebrations
- [ ] Sound effects (optional)
- [ ] Dark mode polish
- [ ] **Google Play Store launch** 🚀

### Future (v2.0+)
- Pig customization & accessories
- Social sharing
- Advanced stats & insights
- Watch OS widget

---

## 🔗 Useful Links

- [Google Play Console](https://play.google.com/console) — Where we publish
- [LottieFiles](https://lottiefiles.com/) — Pre-made animations
- [r/HungryArtists](https://reddit.com/r/HungryArtists) — Commission artists
- [Fiverr](https://fiverr.com) — Freelance artists
- [Material 3 Color Tool](https://m3.material.io/theme-builder) — Design our palette
- [Firebase](https://firebase.google.com/) — Crashlytics, Analytics

---

*Last updated: December 2025*

