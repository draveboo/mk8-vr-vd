#include "cemuvr/reproject.h"
#include "cemuvr/diag.h"

namespace cemuvr {

bool Reprojector::initialize(const VulkanCtx& vk, VkFormat colorFormat, uint32_t w, uint32_t h,
                             VkImage eye0, VkImage eye1) {
    if (m_ready) return true;
    if (!vk.device || !vk.fn.GetDeviceProcAddr || !w || !h) return false;
    PFN_vkGetDeviceProcAddr gdpa = vk.fn.GetDeviceProcAddr;
    m_f.CreateRenderPass = (PFN_vkCreateRenderPass)gdpa(vk.device, "vkCreateRenderPass");
    m_f.DestroyRenderPass = (PFN_vkDestroyRenderPass)gdpa(vk.device, "vkDestroyRenderPass");
    m_f.CreateFramebuffer = (PFN_vkCreateFramebuffer)gdpa(vk.device, "vkCreateFramebuffer");
    m_f.DestroyFramebuffer = (PFN_vkDestroyFramebuffer)gdpa(vk.device, "vkDestroyFramebuffer");
    m_f.CreateShaderModule = (PFN_vkCreateShaderModule)gdpa(vk.device, "vkCreateShaderModule");
    m_f.DestroyShaderModule = (PFN_vkDestroyShaderModule)gdpa(vk.device, "vkDestroyShaderModule");
    m_f.CreatePipelineLayout = (PFN_vkCreatePipelineLayout)gdpa(vk.device, "vkCreatePipelineLayout");
    m_f.DestroyPipelineLayout = (PFN_vkDestroyPipelineLayout)gdpa(vk.device, "vkDestroyPipelineLayout");
    m_f.CreateGraphicsPipelines = (PFN_vkCreateGraphicsPipelines)gdpa(vk.device, "vkCreateGraphicsPipelines");
    m_f.DestroyPipeline = (PFN_vkDestroyPipeline)gdpa(vk.device, "vkDestroyPipeline");
    m_f.CreateDescriptorSetLayout = (PFN_vkCreateDescriptorSetLayout)gdpa(vk.device, "vkCreateDescriptorSetLayout");
    m_f.DestroyDescriptorSetLayout = (PFN_vkDestroyDescriptorSetLayout)gdpa(vk.device, "vkDestroyDescriptorSetLayout");
    m_f.CreateDescriptorPool = (PFN_vkCreateDescriptorPool)gdpa(vk.device, "vkCreateDescriptorPool");
    m_f.DestroyDescriptorPool = (PFN_vkDestroyDescriptorPool)gdpa(vk.device, "vkDestroyDescriptorPool");
    m_f.AllocateDescriptorSets = (PFN_vkAllocateDescriptorSets)gdpa(vk.device, "vkAllocateDescriptorSets");
    m_f.UpdateDescriptorSets = (PFN_vkUpdateDescriptorSets)gdpa(vk.device, "vkUpdateDescriptorSets");
    m_f.CreateSampler = (PFN_vkCreateSampler)gdpa(vk.device, "vkCreateSampler");
    m_f.DestroySampler = (PFN_vkDestroySampler)gdpa(vk.device, "vkDestroySampler");
    m_f.CreateImageView = (PFN_vkCreateImageView)gdpa(vk.device, "vkCreateImageView");
    m_f.DestroyImageView = (PFN_vkDestroyImageView)gdpa(vk.device, "vkDestroyImageView");
    m_f.CmdBeginRenderPass = (PFN_vkCmdBeginRenderPass)gdpa(vk.device, "vkCmdBeginRenderPass");
    m_f.CmdEndRenderPass = (PFN_vkCmdEndRenderPass)gdpa(vk.device, "vkCmdEndRenderPass");
    m_f.CmdBindPipeline = (PFN_vkCmdBindPipeline)gdpa(vk.device, "vkCmdBindPipeline");
    m_f.CmdBindDescriptorSets = (PFN_vkCmdBindDescriptorSets)gdpa(vk.device, "vkCmdBindDescriptorSets");
    m_f.CmdPushConstants = (PFN_vkCmdPushConstants)gdpa(vk.device, "vkCmdPushConstants");
    m_f.CmdSetViewport = (PFN_vkCmdSetViewport)gdpa(vk.device, "vkCmdSetViewport");
    m_f.CmdSetScissor = (PFN_vkCmdSetScissor)gdpa(vk.device, "vkCmdSetScissor");
    m_f.CmdDraw = (PFN_vkCmdDraw)gdpa(vk.device, "vkCmdDraw");
    if (!m_f.CreateRenderPass || !m_f.CreateGraphicsPipelines || !m_f.CreateImageView ||
        !m_f.CmdBeginRenderPass || !m_f.CmdDraw || !m_f.UpdateDescriptorSets) {
        CVR_ERR("reproject", "missing_function=1");
        return false;
    }

    m_colorFormat = colorFormat;
    m_w = w;
    m_h = h;

    VkAttachmentDescription att{};
    att.format = colorFormat;
    att.samples = VK_SAMPLE_COUNT_1_BIT;
    att.loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    att.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    att.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    att.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    att.initialLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    att.finalLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    VkAttachmentReference ref{0, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL};
    VkSubpassDescription sub{};
    sub.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    sub.colorAttachmentCount = 1;
    sub.pColorAttachments = &ref;
    VkRenderPassCreateInfo rpci{VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
    rpci.attachmentCount = 1;
    rpci.pAttachments = &att;
    rpci.subpassCount = 1;
    rpci.pSubpasses = &sub;
    if (m_f.CreateRenderPass(vk.device, &rpci, nullptr, &m_renderPass) != VK_SUCCESS) {
        CVR_ERR("reproject", "create_render_pass_failed=1");
        return false;
    }

    VkDescriptorSetLayoutBinding binds[2]{};
    for (int i = 0; i < 2; ++i) {
        binds[i].binding = (uint32_t)i;
        binds[i].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        binds[i].descriptorCount = 1;
        binds[i].stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    }
    VkDescriptorSetLayoutCreateInfo dsl{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
    dsl.bindingCount = 2;
    dsl.pBindings = binds;
    if (m_f.CreateDescriptorSetLayout(vk.device, &dsl, nullptr, &m_setLayout) != VK_SUCCESS) return false;

    VkPushConstantRange pcr{};
    pcr.stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    pcr.offset = 0;
    pcr.size = sizeof(ReprojectParams);
    VkPipelineLayoutCreateInfo plci{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
    plci.setLayoutCount = 1;
    plci.pSetLayouts = &m_setLayout;
    plci.pushConstantRangeCount = 1;
    plci.pPushConstantRanges = &pcr;
    if (m_f.CreatePipelineLayout(vk.device, &plci, nullptr, &m_layout) != VK_SUCCESS) return false;

    VkShaderModule vs{}, fs{};
    VkShaderModuleCreateInfo smci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
    smci.codeSize = sizeof(kReprojectVertSpv);
    smci.pCode = kReprojectVertSpv;
    if (m_f.CreateShaderModule(vk.device, &smci, nullptr, &vs) != VK_SUCCESS) return false;
    smci.codeSize = sizeof(kReprojectFragSpv);
    smci.pCode = kReprojectFragSpv;
    if (m_f.CreateShaderModule(vk.device, &smci, nullptr, &fs) != VK_SUCCESS) {
        m_f.DestroyShaderModule(vk.device, vs, nullptr);
        return false;
    }

    VkPipelineShaderStageCreateInfo stages[2]{};
    stages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    stages[0].module = vs;
    stages[0].pName = "main";
    stages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    stages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    stages[1].module = fs;
    stages[1].pName = "main";

    VkPipelineVertexInputStateCreateInfo vi{VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO};
    VkPipelineInputAssemblyStateCreateInfo ia{VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO};
    ia.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    VkViewport vp{0, 0, (float)w, (float)h, 0, 1};
    VkRect2D sc{{0, 0}, {w, h}};
    VkPipelineViewportStateCreateInfo vps{VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO};
    vps.viewportCount = 1;
    vps.pViewports = &vp;
    vps.scissorCount = 1;
    vps.pScissors = &sc;
    VkPipelineRasterizationStateCreateInfo rs{VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO};
    rs.polygonMode = VK_POLYGON_MODE_FILL;
    rs.cullMode = VK_CULL_MODE_NONE;
    rs.frontFace = VK_FRONT_FACE_CLOCKWISE;
    rs.lineWidth = 1.0f;
    VkPipelineMultisampleStateCreateInfo ms{VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO};
    ms.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
    VkPipelineColorBlendAttachmentState cba{};
    cba.colorWriteMask = 0xf;
    VkPipelineColorBlendStateCreateInfo cb{VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO};
    cb.attachmentCount = 1;
    cb.pAttachments = &cba;
    VkDynamicState dyn[2]{VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
    VkPipelineDynamicStateCreateInfo ds{VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO};
    ds.dynamicStateCount = 2;
    ds.pDynamicStates = dyn;

    VkGraphicsPipelineCreateInfo gp{VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO};
    gp.stageCount = 2;
    gp.pStages = stages;
    gp.pVertexInputState = &vi;
    gp.pInputAssemblyState = &ia;
    gp.pViewportState = &vps;
    gp.pRasterizationState = &rs;
    gp.pMultisampleState = &ms;
    gp.pColorBlendState = &cb;
    gp.pDynamicState = &ds;
    gp.layout = m_layout;
    gp.renderPass = m_renderPass;
    gp.subpass = 0;
    VkResult pr = m_f.CreateGraphicsPipelines(vk.device, VK_NULL_HANDLE, 1, &gp, nullptr, &m_pipeline);
    m_f.DestroyShaderModule(vk.device, vs, nullptr);
    m_f.DestroyShaderModule(vk.device, fs, nullptr);
    if (pr != VK_SUCCESS) {
        CVR_ERR("reproject", "create_pipeline_failed=%d", (int)pr);
        return false;
    }

    VkSamplerCreateInfo sci{VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO};
    sci.magFilter = VK_FILTER_LINEAR;
    sci.minFilter = VK_FILTER_LINEAR;
    sci.mipmapMode = VK_SAMPLER_MIPMAP_MODE_NEAREST;
    sci.addressModeU = sci.addressModeV = sci.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    sci.maxLod = 0.0f;
    if (m_f.CreateSampler(vk.device, &sci, nullptr, &m_colorSampler) != VK_SUCCESS) return false;
    VkSamplerCreateInfo sci2 = sci;
    sci2.magFilter = sci2.minFilter = VK_FILTER_NEAREST;
    if (m_f.CreateSampler(vk.device, &sci2, nullptr, &m_depthSampler) != VK_SUCCESS) return false;

    VkDescriptorPoolSize ps{};
    ps.type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    ps.descriptorCount = kSets * 2;
    VkDescriptorPoolCreateInfo dpci{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
    dpci.maxSets = kSets;
    dpci.poolSizeCount = 1;
    dpci.pPoolSizes = &ps;
    if (m_f.CreateDescriptorPool(vk.device, &dpci, nullptr, &m_pool) != VK_SUCCESS) return false;
    VkDescriptorSetLayout layouts[kSets]{};
    for (uint32_t i = 0; i < kSets; ++i) layouts[i] = m_setLayout;
    VkDescriptorSetAllocateInfo dsai{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
    dsai.descriptorPool = m_pool;
    dsai.descriptorSetCount = kSets;
    dsai.pSetLayouts = layouts;
    if (m_f.AllocateDescriptorSets(vk.device, &dsai, m_sets) != VK_SUCCESS) return false;

    if (!framebuffer(vk, eye0)) return false;
    if (!framebuffer(vk, eye1)) return false;

    m_ready = true;
    CVR_INFO("reproject", "ready=1 size=%ux%u format=%d sets=%u", w, h, (int)colorFormat, kSets);
    return true;
}

VkImageView Reprojector::colorView(const VulkanCtx& vk, VkImage image, VkFormat format) {
    auto it = m_colorViews.find(image);
    if (it != m_colorViews.end()) return it->second;
    VkImageViewCreateInfo ci{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    ci.image = image;
    ci.viewType = VK_IMAGE_VIEW_TYPE_2D;
    ci.format = format;
    ci.subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
    VkImageView v{};
    if (m_f.CreateImageView(vk.device, &ci, nullptr, &v) != VK_SUCCESS) {
        CVR_ERR("reproject", "color_view_failed=1 image=%p format=%d", (void*)image, (int)format);
        return VK_NULL_HANDLE;
    }
    m_colorViews[image] = v;
    return v;
}

VkImageView Reprojector::depthView(const VulkanCtx& vk, VkImage image, VkFormat format) {
    auto it = m_depthViews.find(image);
    if (it != m_depthViews.end()) return it->second;
    VkImageViewCreateInfo ci{VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    ci.image = image;
    ci.viewType = VK_IMAGE_VIEW_TYPE_2D;
    ci.format = format;
    ci.subresourceRange = {VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1};
    VkImageView v{};
    if (m_f.CreateImageView(vk.device, &ci, nullptr, &v) != VK_SUCCESS) {
        CVR_ERR("reproject", "depth_view_failed=1 image=%p format=%d", (void*)image, (int)format);
        return VK_NULL_HANDLE;
    }
    m_depthViews[image] = v;
    return v;
}

VkFramebuffer Reprojector::framebuffer(const VulkanCtx& vk, VkImage image) {
    auto it = m_framebuffers.find(image);
    if (it != m_framebuffers.end()) return it->second;
    VkImageView v = colorView(vk, image, m_colorFormat);
    if (!v) return VK_NULL_HANDLE;
    VkFramebufferCreateInfo ci{VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
    ci.renderPass = m_renderPass;
    ci.attachmentCount = 1;
    ci.pAttachments = &v;
    ci.width = m_w;
    ci.height = m_h;
    ci.layers = 1;
    VkFramebuffer fb{};
    if (m_f.CreateFramebuffer(vk.device, &ci, nullptr, &fb) != VK_SUCCESS) {
        CVR_ERR("reproject", "framebuffer_failed=1 image=%p", (void*)image);
        return VK_NULL_HANDLE;
    }
    m_framebuffers[image] = fb;
    return fb;
}

bool Reprojector::record(const VulkanCtx& vk, VkCommandBuffer cb,
                         VkImage dst, bool dstWritten, VkImageLayout dstLayout,
                         VkImage color, VkImageLayout colorLayout, VkFormat colorFormat,
                         VkImage depth, VkImageLayout depthLayout, VkFormat depthFormat,
                         const ReprojectParams& params) {
    if (!m_ready) return false;
    VkImageView cv = colorView(vk, color, colorFormat);
    VkImageView dv = depthView(vk, depth, depthFormat);
    VkFramebuffer fb = framebuffer(vk, dst);
    if (!cv || !dv || !fb) return false;

    VkDescriptorSet set = m_sets[m_nextSet];
    m_nextSet = (m_nextSet + 1) % kSets;
    VkDescriptorImageInfo ci{m_colorSampler, cv, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL};
    VkDescriptorImageInfo di{m_depthSampler, dv, VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL};
    VkWriteDescriptorSet w[2]{};
    w[0].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    w[0].dstSet = set;
    w[0].dstBinding = 0;
    w[0].descriptorCount = 1;
    w[0].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    w[0].pImageInfo = &ci;
    w[1].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
    w[1].dstSet = set;
    w[1].dstBinding = 1;
    w[1].descriptorCount = 1;
    w[1].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    w[1].pImageInfo = &di;
    m_f.UpdateDescriptorSets(vk.device, 2, w, 0, nullptr);

    VkImageMemoryBarrier bars[3]{};
    for (auto& b : bars) {
        b.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        b.srcQueueFamilyIndex = b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    }
    const VkImageAspectFlags ca = VK_IMAGE_ASPECT_COLOR_BIT;
    bars[0].image = dst;
    bars[0].oldLayout = dstWritten ? dstLayout : VK_IMAGE_LAYOUT_UNDEFINED;
    bars[0].newLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    bars[0].srcAccessMask = dstWritten ? VK_ACCESS_MEMORY_WRITE_BIT : 0;
    bars[0].dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    bars[0].subresourceRange = {ca, 0, 1, 0, 1};
    bars[1].image = color;
    bars[1].oldLayout = colorLayout;
    bars[1].newLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    bars[1].srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT;
    bars[1].dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    bars[1].subresourceRange = {ca, 0, 1, 0, 1};
    bars[2].image = depth;
    bars[2].oldLayout = depthLayout;
    bars[2].newLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
    bars[2].srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT;
    bars[2].dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
    bars[2].subresourceRange = {VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1};
    vk.fn.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                             0, 0, nullptr, 0, nullptr, 3, bars);

    VkRenderPassBeginInfo rbi{VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
    rbi.renderPass = m_renderPass;
    rbi.framebuffer = fb;
    rbi.renderArea = {{0, 0}, {m_w, m_h}};
    m_f.CmdBeginRenderPass(cb, &rbi, VK_SUBPASS_CONTENTS_INLINE);
    m_f.CmdBindPipeline(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, m_pipeline);
    m_f.CmdBindDescriptorSets(cb, VK_PIPELINE_BIND_POINT_GRAPHICS, m_layout, 0, 1, &set, 0, nullptr);
    m_f.CmdPushConstants(cb, m_layout, VK_SHADER_STAGE_FRAGMENT_BIT, 0, sizeof(ReprojectParams), &params);
    VkViewport vp{0, 0, (float)m_w, (float)m_h, 0, 1};
    VkRect2D sc{{0, 0}, {m_w, m_h}};
    m_f.CmdSetViewport(cb, 0, 1, &vp);
    m_f.CmdSetScissor(cb, 0, 1, &sc);
    m_f.CmdDraw(cb, 3, 1, 0, 0);
    m_f.CmdEndRenderPass(cb);

    bars[0].oldLayout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    bars[0].newLayout = dstLayout;
    bars[0].srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
    bars[0].dstAccessMask = VK_ACCESS_MEMORY_READ_BIT;
    bars[1].oldLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    bars[1].newLayout = colorLayout;
    bars[1].srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
    bars[1].dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    bars[2].oldLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL;
    bars[2].newLayout = depthLayout;
    bars[2].srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
    bars[2].dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    vk.fn.CmdPipelineBarrier(cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                             0, 0, nullptr, 0, nullptr, 3, bars);
    return true;
}

void Reprojector::destroy(const VulkanCtx& vk) {
    if (!vk.device) return;
    for (auto& kv : m_framebuffers) if (m_f.DestroyFramebuffer) m_f.DestroyFramebuffer(vk.device, kv.second, nullptr);
    m_framebuffers.clear();
    for (auto& kv : m_colorViews) if (m_f.DestroyImageView) m_f.DestroyImageView(vk.device, kv.second, nullptr);
    m_colorViews.clear();
    for (auto& kv : m_depthViews) if (m_f.DestroyImageView) m_f.DestroyImageView(vk.device, kv.second, nullptr);
    m_depthViews.clear();
    if (m_pool && m_f.DestroyDescriptorPool) m_f.DestroyDescriptorPool(vk.device, m_pool, nullptr);
    if (m_colorSampler && m_f.DestroySampler) m_f.DestroySampler(vk.device, m_colorSampler, nullptr);
    if (m_depthSampler && m_f.DestroySampler) m_f.DestroySampler(vk.device, m_depthSampler, nullptr);
    if (m_pipeline && m_f.DestroyPipeline) m_f.DestroyPipeline(vk.device, m_pipeline, nullptr);
    if (m_layout && m_f.DestroyPipelineLayout) m_f.DestroyPipelineLayout(vk.device, m_layout, nullptr);
    if (m_setLayout && m_f.DestroyDescriptorSetLayout) m_f.DestroyDescriptorSetLayout(vk.device, m_setLayout, nullptr);
    if (m_renderPass && m_f.DestroyRenderPass) m_f.DestroyRenderPass(vk.device, m_renderPass, nullptr);
    m_pool = VK_NULL_HANDLE;
    m_colorSampler = m_depthSampler = VK_NULL_HANDLE;
    m_pipeline = VK_NULL_HANDLE;
    m_layout = VK_NULL_HANDLE;
    m_setLayout = VK_NULL_HANDLE;
    m_renderPass = VK_NULL_HANDLE;
    m_ready = false;
}

} // namespace cemuvr
