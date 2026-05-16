package net.ai.chatbot.service.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.PromptTemplateDao;
import net.ai.chatbot.dto.admin.PromptTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds default prompt templates into the database on application startup.
 * Only creates templates that don't already exist (by templateCode).
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class PromptTemplateSeeder implements ApplicationRunner {

    private final PromptTemplateDao promptTemplateDao;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking prompt templates...");
        
        List<PromptTemplate> templates = createDefaultTemplates();
        int created = 0;
        
        for (PromptTemplate template : templates) {
            if (!promptTemplateDao.existsByTemplateCode(template.getTemplateCode())) {
                promptTemplateDao.save(template);
                log.info("Created prompt template: {} ({})", template.getName(), template.getTemplateCode());
                created++;
            }
        }
        
        if (created > 0) {
            log.info("Seeded {} prompt templates", created);
        } else {
            log.info("All prompt templates already exist ({} templates)", promptTemplateDao.count());
        }
    }

    private List<PromptTemplate> createDefaultTemplates() {
        List<PromptTemplate> templates = new ArrayList<>();
        int order = 1;

        // ═══════════════════════════════════════════════════════════════════════════
        // ARTICLES & BLOGS
        // ═══════════════════════════════════════════════════════════════════════════

        templates.add(PromptTemplate.builder()
                .templateCode("BLOG_IDEAS")
                .name("Blog Ideas & Outlines")
                .description("Brainstorm new blog post topics that will engage readers and rank well on Google.")
                .emoji("💡")
                .category("Articles & Blogs")
                .outputLabel("Blog Ideas & Outline")
                .promptContent("Generate 5 creative blog post ideas with detailed outlines for the topic \"{{topic}}\"{{#audience}}, targeting {{audience}}{{/audience}}. Tone should be {{tone}}. For each idea, provide: a compelling title, a brief intro paragraph, 4–5 section headings with 2–3 sub-points each, and a conclusion hook.")
                .fields(List.of(
                        field("topic", "Topic or Niche", "e.g. personal finance, vegan cooking", "text", true, 150, null),
                        field("audience", "Target Audience", "e.g. millennials, small business owners", "text", false, 100, null),
                        selectField("tone", "Tone", false, List.of("Professional", "Conversational", "Humorous", "Inspirational", "Educational"))
                ))
                .tags(List.of("blog", "ideas", "outline", "content"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("BLOG_POST")
                .name("Blog Post Writing")
                .description("Write deliciously engaging sections for any topic to grow your blog.")
                .emoji("✍️")
                .category("Articles & Blogs")
                .outputLabel("Blog Post")
                .promptContent("Write a {{length}} blog post titled \"{{title}}\". Keywords/context: {{keywords}}. Tone: {{tone}}. Include an engaging introduction, well-structured body sections with subheadings, and a compelling conclusion with a call-to-action. Use markdown formatting.")
                .fields(List.of(
                        field("title", "Title of your blog article", "Article about the smartphone", "text", true, 190, null),
                        textareaField("keywords", "Keywords or content of your blog", "Story about a horror night, first day at school", true, 190, 3),
                        selectField("tone", "Tone", false, List.of("Professional", "Conversational", "Humorous", "Inspirational", "Formal", "Casual")),
                        selectField("length", "Post Length", false, List.of("Short (300–500 words)", "Medium (600–900 words)", "Long (1000–1500 words)"))
                ))
                .tags(List.of("blog", "writing", "article", "content"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("STORY_WRITING")
                .name("Story Writing")
                .description("Write deliciously creative stories to engage your readers.")
                .emoji("📖")
                .category("Articles & Blogs")
                .outputLabel("Story")
                .promptContent("Write a {{length}} {{genre}} story based on this premise: \"{{premise}}\"{{#characters}}. Main characters: {{characters}}{{/characters}}. Make it vivid, emotionally engaging, with strong dialogue and a surprising twist. Use markdown formatting.")
                .fields(List.of(
                        selectField("genre", "Genre", true, List.of("Fantasy", "Science Fiction", "Romance", "Horror", "Mystery", "Adventure", "Drama", "Comedy")),
                        textareaField("premise", "Story Premise", "A detective who can hear lies must solve a murder...", true, 300, 3),
                        field("characters", "Main Characters (optional)", "Alex — a retired spy; Luna — a street artist", "text", false, 200, null),
                        selectField("length", "Length", false, List.of("Short story (~500 words)", "Medium (~800 words)", "Opening chapter (~1200 words)"))
                ))
                .tags(List.of("story", "creative", "fiction", "writing"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("TEXT_SUMMARIZER")
                .name("Text Summarizer")
                .description("Condense long text into crisp, accurate summaries in seconds.")
                .emoji("📋")
                .category("Articles & Blogs")
                .outputLabel("Summary")
                .promptContent("Summarize the following text in {{length}}:\n\n{{text}}")
                .fields(List.of(
                        textareaField("text", "Text to Summarize", "Paste the text you want summarized…", true, null, 6),
                        selectField("length", "Summary Length", false, List.of("1 sentence", "3 sentences", "1 paragraph", "Bullet points (5)"))
                ))
                .tags(List.of("summarize", "text", "condense"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        // ═══════════════════════════════════════════════════════════════════════════
        // ADS & MARKETING
        // ═══════════════════════════════════════════════════════════════════════════

        templates.add(PromptTemplate.builder()
                .templateCode("GOOGLE_AD")
                .name("Google Ad Copy")
                .description("Create high-performing Google Ad (Title & Description) copy to drive more leads.")
                .emoji("🔵")
                .category("Ads & Marketing")
                .outputLabel("Google Ad Copy")
                .promptContent("Write 5 Google Ad variations for \"{{product}}\"{{#usp}}. USP: {{usp}}{{/usp}}{{#audience}}. Audience: {{audience}}{{/audience}}. For each ad provide: headline 1 (max 30 chars), headline 2 (max 30 chars), headline 3 (max 30 chars), description 1 (max 90 chars), description 2 (max 90 chars). Make them compelling, keyword-rich, and action-oriented.")
                .fields(List.of(
                        field("product", "Product / Service", "Online accounting software for small businesses", "text", true, 150, null),
                        field("usp", "Unique Selling Point", "Saves 3 hours/week, free trial, no credit card", "text", false, 200, null),
                        field("audience", "Target Audience", "Freelancers and solopreneurs", "text", false, 100, null)
                ))
                .tags(List.of("google", "ads", "ppc", "marketing"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("FACEBOOK_AD")
                .name("Facebook Ad Copy")
                .description("Create high-performing Facebook Ad copy to generate more leads.")
                .emoji("🔵")
                .category("Ads & Marketing")
                .outputLabel("Facebook Ad Copy")
                .promptContent("Write 3 Facebook Ad variations for \"{{product}}\"{{#audience}}. Target: {{audience}}{{/audience}}. Goal: {{goal}}{{#offer}}. Offer: {{offer}}{{/offer}}. Each ad should include: a hook (first line that stops the scroll), body copy (2–3 short paragraphs), and a strong CTA. Use an emotional, story-driven tone.")
                .fields(List.of(
                        field("product", "Product / Service", "Online yoga classes for busy moms", "text", true, 150, null),
                        field("audience", "Target Audience", "Women 25–40, interested in fitness", "text", false, 100, null),
                        selectField("goal", "Campaign Goal", false, List.of("Drive traffic", "Generate leads", "Increase sales", "Boost engagement", "App installs")),
                        field("offer", "Offer or CTA", "Free 7-day trial, no credit card required", "text", false, 150, null)
                ))
                .tags(List.of("facebook", "ads", "social", "marketing"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("MARKETING_COPY")
                .name("Marketing Copy & Strategies")
                .description("Generate diverse marketing angles that add intensity to your campaigns.")
                .emoji("📣")
                .category("Ads & Marketing")
                .outputLabel("Marketing Copy")
                .promptContent("Create a marketing copy strategy for \"{{product}}\". Goal: {{goal}}. Channels: {{channels}}. Include: 3 unique positioning angles, value propositions, 5 headline variants, 3 body copy blocks, and a 4-week campaign roadmap.")
                .fields(List.of(
                        field("product", "Product / Service / Brand", "SaaS CRM platform for real estate agents", "text", true, 150, null),
                        field("goal", "Marketing Goal", "Launch campaign, increase trials by 20%", "text", true, 200, null),
                        selectField("channels", "Marketing Channels", false, List.of("Email + Social Media", "Paid Ads", "Content Marketing", "Influencer", "Multi-channel"))
                ))
                .tags(List.of("marketing", "strategy", "copy", "campaign"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("CALL_TO_ACTION")
                .name("Call to Action")
                .description("Write powerful CTAs that convert visitors into customers.")
                .emoji("🎯")
                .category("Ads & Marketing")
                .outputLabel("Call to Action Copy")
                .promptContent("Generate 10 powerful call-to-action variants for \"{{product}}\". Desired action: \"{{action}}\". Style: {{style}}. Include: button text (2–5 words), supporting subtext (1 sentence), and a brief explanation of why each CTA works.")
                .fields(List.of(
                        field("product", "Product / Page", "Free trial landing page for design tool", "text", true, 150, null),
                        field("action", "Desired Action", "Sign up for free trial", "text", true, 100, null),
                        selectField("style", "Style", false, List.of("Urgent", "Benefit-focused", "Curiosity-driven", "Social proof", "Fear of missing out"))
                ))
                .tags(List.of("cta", "conversion", "button", "marketing"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("LANDING_PAGE")
                .name("Landing Page & Website Copy")
                .description("Generate creative and persuasive copy for sections of your website.")
                .emoji("💻")
                .category("Ads & Marketing")
                .outputLabel("Landing Page Copy")
                .promptContent("Write compelling {{section}} copy for \"{{product}}\"{{#audience}}. Target audience: {{audience}}{{/audience}}. Make it conversion-focused, clear, and benefit-driven. Use markdown formatting with appropriate headings.")
                .fields(List.of(
                        field("product", "Product / Service", "Project management tool for remote teams", "text", true, 150, null),
                        selectField("section", "Page Section", true, List.of("Hero section", "Features section", "Benefits section", "Testimonials prompt", "FAQ section", "Pricing section", "Full landing page")),
                        field("audience", "Target Audience", "Startup founders and team leads", "text", false, 100, null)
                ))
                .tags(List.of("landing", "website", "copy", "conversion"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        // ═══════════════════════════════════════════════════════════════════════════
        // E-COMMERCE
        // ═══════════════════════════════════════════════════════════════════════════

        templates.add(PromptTemplate.builder()
                .templateCode("PRODUCT_DESCRIPTION")
                .name("Product Description")
                .description("Craft epic product descriptions that increase conversions on your store.")
                .emoji("🛒")
                .category("E-commerce")
                .outputLabel("Product Description")
                .promptContent("Write a compelling {{platform}} product description for \"{{product}}\". Features: {{features}}{{#audience}}. Target customer: {{audience}}{{/audience}}. Include: attention-grabbing headline, benefit-driven intro, bullet-point features list, emotional connection paragraph, and a strong CTA. Optimize for conversions and SEO.")
                .fields(List.of(
                        field("product", "Product Name", "Wireless noise-cancelling headphones", "text", true, 100, null),
                        textareaField("features", "Key Features", "40hr battery, foldable, 30mm drivers, USB-C", true, 300, 3),
                        field("audience", "Target Customer", "Commuters and remote workers", "text", false, 100, null),
                        selectField("platform", "Platform", false, List.of("Amazon", "Shopify store", "Etsy", "WooCommerce", "General"))
                ))
                .tags(List.of("product", "ecommerce", "description", "conversion"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("AMAZON_LISTING")
                .name("Amazon Product Outlines")
                .description("Create product descriptions according to Amazon's guidelines to grab more sales.")
                .emoji("🛍️")
                .category("E-commerce")
                .outputLabel("Amazon Product Listing")
                .promptContent("Write a complete Amazon product listing for \"{{product}}\"{{#category}} in category \"{{category}}\"{{/category}}. Features: {{features}}. Include: SEO-optimized product title (max 200 chars), 5 bullet points (starting with key benefit in caps), A+ content description (2–3 paragraphs), and 10 backend search keywords.")
                .fields(List.of(
                        field("product", "Product Name", "Stainless steel water bottle 32oz", "text", true, 100, null),
                        textareaField("features", "Key Features & Benefits", "BPA-free, keeps cold 24hr, dishwasher safe", true, 300, 3),
                        field("category", "Amazon Category", "Sports & Outdoors", "text", false, 80, null)
                ))
                .tags(List.of("amazon", "listing", "ecommerce", "seo"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("PRODUCT_REVIEW")
                .name("Product Reviews & Responders")
                .description("Write detailed product reviews and thoughtful responses to existing reviews.")
                .emoji("⭐")
                .category("E-commerce")
                .outputLabel("Product Review / Response")
                .promptContent("Task: {{task}} for \"{{product}}\". Context: {{context}}. Write a professional, authentic, and empathetic response. For reviews, include pros, cons, and a verdict. For responses, thank the customer, address their feedback specifically, and offer resolution if negative.")
                .fields(List.of(
                        selectField("task", "What do you need?", true, List.of("Write a product review", "Respond to a positive review", "Respond to a negative review", "Respond to a neutral review")),
                        field("product", "Product Name", "Smart LED desk lamp", "text", true, 100, null),
                        textareaField("context", "Context / Review Text", "Product highlights or the review you want to respond to...", true, 400, 3)
                ))
                .tags(List.of("review", "response", "customer", "ecommerce"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        // ═══════════════════════════════════════════════════════════════════════════
        // GENERAL WRITING
        // ═══════════════════════════════════════════════════════════════════════════

        templates.add(PromptTemplate.builder()
                .templateCode("EMAIL_WRITING")
                .name("Email Writing")
                .description("Create professional emails for marketing, sales, announcements & more.")
                .emoji("📧")
                .category("General Writing")
                .outputLabel("Email")
                .promptContent("Write a {{tone}} {{purpose}} email. Details: {{context}}{{#recipient}}. Recipient: {{recipient}}{{/recipient}}. Include: compelling subject line, personalized opening, clear value in the body, and a strong CTA. Keep it concise and scannable.")
                .fields(List.of(
                        selectField("purpose", "Email Purpose", true, List.of("Sales outreach", "Marketing campaign", "Follow-up", "Announcement", "Customer onboarding", "Apology / recovery", "Re-engagement", "Thank you")),
                        textareaField("context", "Context / Details", "Launching a new feature: dark mode for our project app...", true, 300, 3),
                        field("recipient", "Recipient", "Existing customers, software teams", "text", false, 100, null),
                        selectField("tone", "Tone", false, List.of("Professional", "Friendly", "Urgent", "Inspirational", "Casual"))
                ))
                .tags(List.of("email", "writing", "communication", "business"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("BUSINESS_IDEAS")
                .name("Business Ideas & Strategies")
                .description("Explore business ideas and strategies you should pursue as an entrepreneur.")
                .emoji("💼")
                .category("General Writing")
                .outputLabel("Business Ideas & Strategy")
                .promptContent("Generate 5 innovative business ideas in the \"{{niche}}\" space{{#skills}} for someone with: {{skills}}{{/skills}}. Goal: {{goal}}. For each idea: concept overview, target market, revenue model, startup cost estimate, and 90-day launch roadmap.")
                .fields(List.of(
                        field("niche", "Industry or Niche", "Sustainable fashion, EdTech, HealthTech", "text", true, 150, null),
                        field("skills", "Your Skills / Resources", "Software development, $5k budget, 10hr/week", "text", false, 200, null),
                        selectField("goal", "Goal", false, List.of("Side income", "Full-time business", "Startup / VC-backed", "Freelance / consulting", "E-commerce"))
                ))
                .tags(List.of("business", "ideas", "strategy", "entrepreneur"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("BRAND_NAME")
                .name("Brand Name Generator")
                .description("Give your new brand launch the perfect name that captures attention.")
                .emoji("🏷️")
                .category("General Writing")
                .outputLabel("Brand Name Ideas")
                .promptContent("Generate 15 brand name ideas for: \"{{description}}\". Style preference: {{style}}{{#keywords}}. Consider keywords: {{keywords}}{{/keywords}}. For each name provide: the name, a 1-sentence rationale, potential domain availability note (.com suggestion), and tagline idea.")
                .fields(List.of(
                        textareaField("description", "Describe Your Business", "AI-powered fitness coaching app for busy professionals", true, 200, 2),
                        selectField("style", "Name Style", false, List.of("Short & punchy", "Descriptive", "Abstract / invented", "Metaphorical", "Acronym-friendly")),
                        field("keywords", "Must-include Keywords (optional)", "fit, motion, coach", "text", false, 100, null)
                ))
                .tags(List.of("brand", "name", "naming", "branding"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("TAGLINE")
                .name("Tagline & Headlines")
                .description("Generate catchy and creative taglines for your profiles, brand, or products.")
                .emoji("✨")
                .category("General Writing")
                .outputLabel("Taglines & Headlines")
                .promptContent("Generate 12 taglines and 8 headline variants for \"{{brand}}\"{{#value}}. Core value: {{value}}{{/value}}. Style: {{style}}. Group them into: Taglines (short, memorable, brandable) and Headlines (for ads, landing pages, emails). Explain the hook behind the best 3.")
                .fields(List.of(
                        field("brand", "Brand / Product / Page", "Productivity app for remote teams", "text", true, 150, null),
                        field("value", "Core Value or Benefit", "Saves 2 hours a day, reduces meeting overload", "text", false, 150, null),
                        selectField("style", "Style", false, List.of("Clever & witty", "Inspirational", "Bold & direct", "Question-based", "Minimalist"))
                ))
                .tags(List.of("tagline", "headline", "slogan", "branding"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("CONTENT_IMPROVER")
                .name("Content Improver")
                .description("Rewrite and elevate your existing content to be more compelling and polished.")
                .emoji("⬆️")
                .category("General Writing")
                .outputLabel("Improved Content")
                .promptContent("Rewrite and improve the following content to be {{goal}}. Preserve the original meaning and key points while significantly enhancing the quality:\n\n{{content}}\n\nAlso briefly explain 3 key improvements you made.")
                .fields(List.of(
                        textareaField("content", "Your Existing Content", "Paste the content you want to improve…", true, null, 5),
                        selectField("goal", "Improvement Goal", true, List.of("More engaging", "More professional", "Simpler / clearer", "SEO optimized", "More persuasive", "Shorter / concise"))
                ))
                .tags(List.of("improve", "rewrite", "enhance", "content"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("CONTENT_REPHRASE")
                .name("Content Rephrase")
                .description("Rephrase sentences and paragraphs in a fresh, unique voice.")
                .emoji("🔄")
                .category("General Writing")
                .outputLabel("Rephrased Content")
                .promptContent("Rephrase the following text in a {{tone}} tone, making it sound fresh and original while preserving the original meaning:\n\n{{content}}")
                .fields(List.of(
                        textareaField("content", "Text to Rephrase", "Paste the text you want rephrased…", true, null, 5),
                        selectField("tone", "Target Tone", false, List.of("Professional", "Casual / friendly", "Academic", "Simple / plain", "Creative", "Formal"))
                ))
                .tags(List.of("rephrase", "rewrite", "paraphrase", "content"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        // ═══════════════════════════════════════════════════════════════════════════
        // PROFILE & BIO
        // ═══════════════════════════════════════════════════════════════════════════

        templates.add(PromptTemplate.builder()
                .templateCode("LINKEDIN_PROFILE")
                .name("LinkedIn Profile Copy")
                .description("Add a blend of achievements and professionalism to enrich your LinkedIn.")
                .emoji("🔵")
                .category("Profile & Bio")
                .outputLabel("LinkedIn Profile Copy")
                .promptContent("Write a compelling LinkedIn profile for: Role: \"{{role}}\". Experience: {{experience}}. Goal: {{goal}}. Include: headline (max 220 chars), about section (3 paragraphs, first-person), experience bullet points (action + result format), and 5 featured skills to highlight.")
                .fields(List.of(
                        field("role", "Your Role / Title", "Senior Product Manager at a B2B SaaS company", "text", true, 150, null),
                        textareaField("experience", "Key Experience & Achievements", "Led team of 12, launched 3 products, 40% revenue growth", true, 300, 3),
                        selectField("goal", "Profile Goal", false, List.of("Land a new job", "Attract clients / consulting", "Build thought leadership", "Network in industry", "Recruit talent"))
                ))
                .tags(List.of("linkedin", "profile", "professional", "bio"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("PERSONAL_BIO")
                .name("Personal Bio")
                .description("Describe yourself effectively in a few words while capturing attention.")
                .emoji("👤")
                .category("Profile & Bio")
                .outputLabel("Personal Bio")
                .promptContent("Write a {{length}} personal bio for {{name}}, who is a {{role}}{{#highlights}}. Highlights: {{highlights}}{{/highlights}}. Make it confident, engaging, and memorable. Write in third person.")
                .fields(List.of(
                        field("name", "Your Name", "Alex Johnson", "text", true, 60, null),
                        field("role", "Role / What You Do", "UX Designer & Illustrator based in NYC", "text", true, 150, null),
                        textareaField("highlights", "Key Highlights / Achievements", "5 years at Google, featured in Forbes, mentor at GDI", false, 200, 2),
                        selectField("length", "Bio Length", false, List.of("Twitter / Instagram (2 lines)", "Website / portfolio (1 paragraph)", "Conference speaker (2 paragraphs)", "Full professional bio (3 paragraphs)"))
                ))
                .tags(List.of("bio", "personal", "profile", "about"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        // ═══════════════════════════════════════════════════════════════════════════
        // JOBS & COMPANIES
        // ═══════════════════════════════════════════════════════════════════════════

        templates.add(PromptTemplate.builder()
                .templateCode("COVER_LETTER")
                .name("Cover Letter")
                .description("Write a compelling, personalized cover letter that gets you noticed.")
                .emoji("📄")
                .category("Jobs & Companies")
                .outputLabel("Cover Letter")
                .promptContent("Write a professional, tailored cover letter for the role of \"{{role}}\". Candidate experience: {{experience}}{{#company}}. Motivation for this company: {{company}}{{/company}}. Include: compelling opening hook, 2 achievement-focused body paragraphs, company-specific connection, and a confident closing. Keep it under 350 words.")
                .fields(List.of(
                        field("role", "Job Role Applying For", "Senior Frontend Engineer at Stripe", "text", true, 150, null),
                        textareaField("experience", "Your Relevant Experience", "4 years React, launched 2 fintech products, TypeScript expert", true, 300, 3),
                        field("company", "Why This Company?", "Admire their API-first approach and engineering culture", "text", false, 200, null)
                ))
                .tags(List.of("cover", "letter", "job", "application"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("JOB_DESCRIPTION")
                .name("Job Description")
                .description("Create clear, attractive job descriptions to find the best candidates.")
                .emoji("📋")
                .category("Jobs & Companies")
                .outputLabel("Job Description")
                .promptContent("Write an engaging job description for \"{{role}}\" at: {{company}}. Requirements: {{requirements}}{{#perks}}. Perks: {{perks}}{{/perks}}. Include: hook intro about the company, role overview, responsibilities (5–7 bullets), requirements (must-have + nice-to-have), and perks section. Make it inclusive and appealing.")
                .fields(List.of(
                        field("role", "Job Title", "Senior Backend Engineer", "text", true, 100, null),
                        field("company", "Company & Team Description", "Early-stage fintech startup, 20-person engineering team", "text", true, 200, null),
                        textareaField("requirements", "Key Requirements", "5+ years Go, microservices, Kubernetes, strong comms", true, 300, 3),
                        field("perks", "Perks & Benefits", "Remote-first, equity, $5k learning budget", "text", false, 200, null)
                ))
                .tags(List.of("job", "description", "hiring", "recruitment"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        // ═══════════════════════════════════════════════════════════════════════════
        // SEO & WEB
        // ═══════════════════════════════════════════════════════════════════════════

        templates.add(PromptTemplate.builder()
                .templateCode("KEYWORD_GENERATOR")
                .name("Keyword Generator")
                .description("Come up with relevant, key phrases and questions related to your niche.")
                .emoji("🔍")
                .category("SEO & Web")
                .outputLabel("Keyword List")
                .promptContent("Generate {{count}} for the topic \"{{topic}}\" with {{intent}} search intent. Organize them into clusters: short-tail (1–2 words), long-tail (3–5 words), and question-based. Include estimated search intent label and content type suggestion for each.")
                .fields(List.of(
                        field("topic", "Topic or Seed Keyword", "electric vehicles, remote work tools", "text", true, 150, null),
                        selectField("intent", "Search Intent", false, List.of("Informational", "Commercial / comparison", "Transactional", "Navigational", "All intents")),
                        selectField("count", "Number of Keywords", false, List.of("20 keywords", "50 keywords", "100 keywords"))
                ))
                .tags(List.of("keyword", "seo", "research", "search"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("KEYWORD_EXTRACTOR")
                .name("Keyword Extractor")
                .description("Extract the most important SEO keywords from any text automatically.")
                .emoji("🔎")
                .category("SEO & Web")
                .outputLabel("Extracted Keywords")
                .promptContent("Extract the {{count}} most important SEO keywords from the following text. For each keyword provide: the keyword/phrase, relevance score (1–10), search intent (informational/commercial/transactional), and a brief note on why it's important:\n\n{{text}}")
                .fields(List.of(
                        textareaField("text", "Text to Extract Keywords From", "Paste your article, product page, or any content…", true, null, 5),
                        selectField("count", "Number of Keywords", false, List.of("Top 10", "Top 20", "Top 30"))
                ))
                .tags(List.of("keyword", "extract", "seo", "analysis"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        templates.add(PromptTemplate.builder()
                .templateCode("SEO_META")
                .name("SEO Meta Title & Details")
                .description("Generate SEO-optimized page titles and meta descriptions that rank and click.")
                .emoji("🌐")
                .category("SEO & Web")
                .outputLabel("SEO Meta Tags")
                .promptContent("Generate {{count}} of SEO-optimized meta title and description for \"{{page}}\"{{#keywords}}. Target keywords: {{keywords}}{{/keywords}}. Each variant should include: meta title (max 60 chars), meta description (max 155 chars), and a focus keyword. Ensure each is unique in angle and compelling to click.")
                .fields(List.of(
                        field("page", "Page / Article Title or Topic", "Best productivity apps for remote teams 2024", "text", true, 150, null),
                        field("keywords", "Target Keywords", "productivity apps, remote work tools, team collaboration", "text", false, 150, null),
                        selectField("count", "Variants", false, List.of("3 variants", "5 variants", "10 variants"))
                ))
                .tags(List.of("seo", "meta", "title", "description"))
                .displayOrder(order++)
                .isActive(true)
                .build());

        return templates;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private PromptTemplate.TemplateField field(String id, String label, String placeholder, String type, boolean required, Integer maxLength, Integer rows) {
        return PromptTemplate.TemplateField.builder()
                .id(id)
                .label(label)
                .placeholder(placeholder)
                .type(type)
                .required(required)
                .maxLength(maxLength)
                .rows(rows)
                .build();
    }

    private PromptTemplate.TemplateField textareaField(String id, String label, String placeholder, boolean required, Integer maxLength, Integer rows) {
        return PromptTemplate.TemplateField.builder()
                .id(id)
                .label(label)
                .placeholder(placeholder)
                .type("textarea")
                .required(required)
                .maxLength(maxLength)
                .rows(rows != null ? rows : 3)
                .build();
    }

    private PromptTemplate.TemplateField selectField(String id, String label, boolean required, List<String> options) {
        return PromptTemplate.TemplateField.builder()
                .id(id)
                .label(label)
                .type("select")
                .required(required)
                .options(options)
                .build();
    }
}
