# 🐷 Oink

*A habit motivation app powered by behavioral economics - because losing money stinks.*

> 🤖 **Built with AI.**
> This entire project was coded with AI.
> Every layer - architecture, Compose UI, the Room data layer and migrations, tests, and this README - was written through human-AI collaboration using Cursor and Claude Code with Claude.
> See [Built with AI](#built-with-ai) for details.

## What is Oink?

Oink helps you build consistent daily habits while earning a real fun-money budget.

Track any habit you want to do every day - work out, meditate, read, practice an instrument - or any habit you want to *stop*, like doom-scrolling or smoking.
Every habit you keep pays into one shared "guilt-free purchase" fund.
Miss a day and that day's habit **halves** your balance.

Want that new guitar?
Those fancy darts?
A weekend trip?
**Earn it by showing up.**
Your adorable pig companion gets stronger as your balance grows - and smaller when you slip.

### Track Many Habits, One Piggy Bank

Oink started as a workout tracker and grew into a general habit tracker.
You can run as many habits as you like, side by side, each with its own emoji, daily reward, streak, and streak freezes.

Every habit comes in one of two flavors:

- 🌱 **Build habits** - things you want to *start* doing daily (workout, read, meditate). You act to claim the day; an unlogged past day stays a miss.
- 🚫 **Quit habits** - things you want to *stop* doing (no sugar, no scrolling). Getting through the day clean is the win; a day you don't report a slip resolves to a success.

Both types share the same ledger and the same halving math - a quit "slip" is arithmetically identical to a build "miss."
All of your habits feed **one shared fun-money balance**, so consistency across your whole routine is what fills the bank.

Habits you'd rather keep to yourself can be marked **private** and tucked behind a lock, out of the main list.

### The Real Money Angle

**Important**: The money in Oink represents your *actual* fun-money budget.

This isn't virtual points - it's an **honor system budget tracker**:
- 💪 Keep your habits consistently → Build real purchasing power
- 🎸 Want something non-essential? → Check your Oink balance
- 💰 Cash out when ready → Actually buy that thing you earned

Think of it like this: every successful day is worth a few dollars (you set the value per habit) of real money you're allowing yourself to budget for non-essentials.
The app keeps you honest about what you've earned.

### Any Effort Counts

Oink doesn't care if you ran a marathon or did 10 push-ups, read a chapter or a single page.
**Consistency beats intensity.**

The goal is to show up *with intention* every single day - whether that's:
- A full gym session or a 15-minute walk
- Ten minutes of reading
- A short meditation or breathing exercise
- Practicing an instrument
- Simply getting through the day without the thing you're quitting

What matters is showing up deliberately.
We're building habits, not training for the Olympics.
The halving mechanic keeps you honest - you decide what counts.

## How It Works

### The Rules

1. **Every habit starts at $0.00** and pays into your shared balance.
2. **Did you keep the habit today?**
   - ✅ Yes → Add that habit's reward (default $5.00) to your balance
   - ❌ No → Balance ÷ 2
3. **One check-in per habit per day** (can't game the system)
4. **Watch your pig get swole** 💪
5. **Cash out when ready** → Actually buy that thing you earned! 🎉

### Streak Freezes ❄️

Life happens.
Sick days, travel, emergencies - sometimes you legitimately can't keep a habit.

**Streak Freezes** let you protect a habit's streak when you miss a day:
- Cost **2× that habit's reward** ($10 at default settings)
- Hold up to **2 freezes** per habit at a time
- Use them retroactively when you miss a day

Freezes cost double because they should feel like insurance, not a loophole.
You're paying "two days' worth" to skip one - expensive enough to discourage abuse, cheap enough to save you from genuine emergencies.

The goal isn't to punish you for missing a day.
It's to keep you coming back.

### Example Journey
```
Day 1: Workout ✅ +$5.00, Read ✅ +$5.00     → $10.00
Day 2: Workout ✅, Read ✅                    → $20.00
Day 3: Workout ✅, Read skipped (÷2)          → $22.50
Day 4: Workout ✅, Read ✅                    → $32.50
Day 5: Both ✅, No-sugar clean day ✅         → $50.00
Day 6: Cash out $20 for a new video game      → $30.00 remaining
```

The halving mechanic means:
- Missing one day hurts, but you can recover
- Long streaks become valuable to protect
- Spending your earned money is celebration time! 🎊

## The Psychology Behind It

### Loss Aversion

The core mechanic leverages **loss aversion** - the principle that losses feel roughly twice as painful as equivalent gains feel good[^1][^2].
When you miss a day, you don't just fail to gain your reward, you **lose half your balance**.
Your brain fights harder to protect what you've earned than it would to build it in the first place.

This has been validated across 19 countries[^3] and is one of the most influential concepts in behavioral science.

### Why Gamification Works

Research shows gamification dramatically improves habit consistency:
- **60% boost** in commitment when progress is highly visible[^4]
- **3.6x more likely** to stay engaged long-term after maintaining a 7-day streak[^4]
- **3x more likely** to maintain habits with clear recovery mechanisms[^5]

### Inspired By

Oink combines proven patterns from successful habit apps:
- **Duolingo**: Streak mechanics and visible progress
- **Finch**: Pet-care metaphor that creates emotional investment[^6]
- **Habitica**: Gamified progression for routine tasks[^7]
- **StickK**: Financial commitment devices[^8]

But Oink has a unique twist: **you're building toward rewards (things you want to buy) while protecting against losses (the halving mechanic)**.
Unlike penalty apps where you risk your own money upfront, you start at zero and earn your way up.
Unlike virtual reward apps, your balance represents real purchasing power.
It's loss aversion applied to *gains you've earned*, not money you started with.

## Design Principles

Every feature follows these psychology-backed guidelines:

1. **Positive Reinforcement** - Celebrate wins, frame recovery as achievable, no shame or guilt[^6]
2. **Visual Progress** - The pig's appearance reflects your commitment, animations create delight
3. **Recovery Friendly** - Halving hurts but you can always bounce back (3x better retention)[^5]
4. **Clear Feedback** - Always know what you'll gain (+$5) or lose (÷2) before checking in
5. **Honor System** - You commit to yourself; the app tracks, you manage the actual money

## Milestones & Achievements

**Financial Milestones**:
- 💰 $25 - Quarter Pounder
- 💰 $50 - Half-Swole
- 💰 $100 - Century Club
- 💰 $200 - Swole Savings

**Streak Milestones**:
- 🔥 7 days - Week Warrior
- 🔥 30 days - Monthly Muscle
- 🔥 60 days - Habit Hero

**Cash-Out Achievements**:
- 🎁 First Purchase - "Earned It"
- 🎁 $200 total spent - "Living the Dream"

## Use Cases

What people are earning:
- 🎸 Musical instruments and gear
- 🎯 Hobby equipment (darts, board games, sports gear)
- 🍽️ Nice meals or special ingredients
- 📚 Books, courses, learning materials
- 🎮 Video games or entertainment
- ✈️ Weekend trips or experiences

**The rule**: If it's non-essential but would bring you joy, earn it through consistent habits.

## Tech Stack

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Repository, with `StateFlow`-backed UI state and manual dependency injection (no Hilt)
- **Database**: Room + KSP, with versioned migrations for safe upgrades
- **Persistence**: DataStore Preferences for settings and per-habit state
- **Background work**: WorkManager for reminders and daily rollovers
- **Home screen**: Glance widgets for per-habit check-ins
- **Navigation**: Navigation Compose
- **Min SDK**: 26

## Why "Oink"?

The piggy bank is the universal symbol of saving money.
Oink combines that metaphor with a character that grows stronger with you, plus the satisfaction of breaking it open when you cash out.

Plus, "Did you Oink today?" is way more fun than "Did you keep your habits today?"

## Contributing

This is a personal project to help build habit consistency while earning guilt-free fun purchases.
Feel free to:
- Report bugs or suggest features
- Share your experience using Oink
- Propose psychology-backed improvements
- Share what you're saving up for!

## License

MIT License

## References

[^1]: Kahneman, D., & Tversky, A. (1984). Choices, values, and frames. *American Psychologist*, 39(4), 341-350. https://doi.org/10.1037/0003-066X.39.4.341

[^2]: Kahneman, D., & Tversky, A. (1979). Prospect theory: An analysis of decision under risk. *Econometrica*, 47(2), 263-291. https://doi.org/10.2307/1914185

[^3]: Ruggeri, K., et al. (2022). Replicating patterns of prospect theory for decision under risk. *Nature Human Behaviour*, 6(10), 1409-1417. https://doi.org/10.1038/s41562-022-01392-w

[^4]: Orizon Design. (2025). Duolingo's Gamification Secrets: How Streaks & XP Boost Engagement by 60%. https://www.orizon.co/blog/duolingos-gamification-secrets

[^5]: Liberty, S. (2025). How To Actually Gamify Your Life (A Game Designer's Guide). *Medium*. https://sa-liberty.medium.com/how-to-actually-gamify-your-life-a-game-designers-guide-e54cd91c79b1

[^6]: Naavik. (2024). New Horizons in Habit-Building Gamification. https://naavik.co/deep-dives/deep-dives-new-horizons-in-gamification/

[^7]: Habitica. https://habitica.com/

[^8]: BehavioralEconomics.com. (2024). Loss Aversion. https://www.behavioraleconomics.com/resources/mini-encyclopedia-of-be/loss-aversion/

## Further Reading

**Core Behavioral Economics**:
- Kahneman, D. (2011). *Thinking, Fast and Slow*. Farrar, Straus and Giroux.
- Thaler, R. H., & Sunstein, C. R. (2008). *Nudge*. Yale University Press.

**Gamification & Habits**:
- Clear, J. (2018). *Atomic Habits*. Avery.
- Eyal, N. (2014). *Hooked*. Portfolio.

**Research**:
- Lieder, F., et al. (2022). Gamification of Behavior Change. *Journal of Medical Internet Research*, 24(4). https://www.ncbi.nlm.nih.gov/pmc/articles/PMC10998180/

## Acknowledgments

Built with inspiration from behavioral economics research and proven gamification patterns.
Special thanks to Kahneman & Tversky for prospect theory, and to the teams behind Duolingo, Finch, Habitica, and StickK for showing what works.

### Built with AI

This project was coded end to end with AI.
It began in [Cursor](https://cursor.com) with Claude as the AI pair programmer and continued with [Claude Code](https://claude.com/claude-code), also powered by Claude.
From architecture decisions to Compose UI, the Room data model and its migrations, widget behavior, tests, and debugging, the entire codebase was a human-AI collaboration.
The psychology-driven design principles, behavioral economics integration, and Kotlin/Compose implementation were all shaped through iterative AI-assisted development.

---

**Remember**: The pig gets swole when you do. 💪🐷

*Oink: Because your brain is weird about money, and we can use that for good.*
