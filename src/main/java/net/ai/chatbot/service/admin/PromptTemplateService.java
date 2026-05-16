package net.ai.chatbot.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.ai.chatbot.dao.admin.PromptTemplateDao;
import net.ai.chatbot.dto.admin.PromptTemplate;
import net.ai.chatbot.dto.admin.request.CreatePromptTemplateRequest;
import net.ai.chatbot.utils.AuthUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptTemplateDao promptTemplateDao;

    public PromptTemplate createTemplate(CreatePromptTemplateRequest request) {
        if (promptTemplateDao.existsByTemplateCode(request.getTemplateCode())) {
            throw new RuntimeException("Template code already exists: " + request.getTemplateCode());
        }

        PromptTemplate template = PromptTemplate.builder()
                .templateCode(request.getTemplateCode())
                .name(request.getName())
                .description(request.getDescription())
                .emoji(request.getEmoji())
                .category(request.getCategory())
                .subcategory(request.getSubcategory())
                .outputLabel(request.getOutputLabel())
                .promptContent(request.getPromptContent())
                .fields(request.getFields())
                .outputFormat(request.getOutputFormat())
                .exampleOutput(request.getExampleOutput())
                .isActive(request.isActive())
                .isPremium(request.isPremium())
                .requiredPlanIds(request.getRequiredPlanIds())
                .iconUrl(request.getIconUrl())
                .iconClass(request.getIconClass())
                .tags(request.getTags())
                .displayOrder(request.getDisplayOrder())
                .createdBy(AuthUtils.getEmail())
                .build();

        log.info("Creating prompt template: {}", template.getTemplateCode());
        return promptTemplateDao.save(template);
    }

    public PromptTemplate getTemplateById(String id) {
        return promptTemplateDao.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found: " + id));
    }

    public PromptTemplate getTemplateByCode(String templateCode) {
        return promptTemplateDao.findByTemplateCode(templateCode)
                .orElseThrow(() -> new RuntimeException("Template not found: " + templateCode));
    }

    public List<PromptTemplate> getAllTemplates(int page, int size, String category, Boolean isActive) {
        return promptTemplateDao.findAll(page, size, category, isActive);
    }

    public long countTemplates(String category, Boolean isActive) {
        return promptTemplateDao.count(category, isActive);
    }

    public List<PromptTemplate> getTemplatesByCategory(String category) {
        return promptTemplateDao.findByCategory(category);
    }

    public List<String> getAllCategories() {
        return promptTemplateDao.findAllCategories();
    }

    public List<PromptTemplate> getPopularTemplates(int limit) {
        return promptTemplateDao.findPopular(limit);
    }

    public List<PromptTemplate> searchTemplates(String keyword) {
        return promptTemplateDao.search(keyword);
    }

    public PromptTemplate updateTemplate(String id, CreatePromptTemplateRequest request) {
        PromptTemplate template = getTemplateById(id);

        if (!template.getTemplateCode().equals(request.getTemplateCode()) &&
                promptTemplateDao.existsByTemplateCode(request.getTemplateCode())) {
            throw new RuntimeException("Template code already exists: " + request.getTemplateCode());
        }

        template.setTemplateCode(request.getTemplateCode());
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setEmoji(request.getEmoji());
        template.setCategory(request.getCategory());
        template.setSubcategory(request.getSubcategory());
        template.setOutputLabel(request.getOutputLabel());
        template.setPromptContent(request.getPromptContent());
        template.setFields(request.getFields());
        template.setOutputFormat(request.getOutputFormat());
        template.setExampleOutput(request.getExampleOutput());
        template.setActive(request.isActive());
        template.setPremium(request.isPremium());
        template.setRequiredPlanIds(request.getRequiredPlanIds());
        template.setIconUrl(request.getIconUrl());
        template.setIconClass(request.getIconClass());
        template.setTags(request.getTags());
        template.setDisplayOrder(request.getDisplayOrder());

        log.info("Updating prompt template: {}", template.getTemplateCode());
        return promptTemplateDao.save(template);
    }

    public void useTemplate(String templateId) {
        log.info("Recording usage for template: {}", templateId);
        promptTemplateDao.incrementUsageCount(templateId);
    }

    public void setActive(String id, boolean isActive) {
        log.info("Setting template {} active status to {}", id, isActive);
        promptTemplateDao.setActive(id, isActive);
    }

    public void deleteTemplate(String id) {
        log.info("Deleting template: {}", id);
        promptTemplateDao.delete(id);
    }
}
