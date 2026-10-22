#pragma once

#define NOMINMAX
#include <windows.h>
#ifndef VK_USE_PLATFORM_WIN32_KHR
#define VK_USE_PLATFORM_WIN32_KHR
#endif
#include <vulkan/vulkan.h>

#include <cstdint>
#include <map>

#include "cemuvr/interop.h"
#include "cemuvr/reproject_spv.h"

namespace cemuvr {

struct ReprojectParams {
    float shift[4]{0.0f, 0.0f, 1.0f, 0.0f}; // x,y = uv shift at near, w = debug
};

// Depth-based per-eye reprojection. Samples the guest colour and the live
// scene depth and renders an eye image that carries real depth parallax.
class Reprojector {
public:
    bool initialize(const VulkanCtx& vk, VkFormat colorFormat, uint32_t w, uint32_t h,
                    VkImage eye0, VkImage eye1);
    bool ready() const { return m_ready; }

    bool record(const VulkanCtx& vk, VkCommandBuffer cb,
                VkImage dst, bool dstWritten, VkImageLayout dstLayout,
                VkImage color, VkImageLayout colorLayout, VkFormat colorFormat,
                VkImage depth, VkImageLayout depthLayout, VkFormat depthFormat,
                const ReprojectParams& params);

    void destroy(const VulkanCtx& vk);

private:
    struct Fns {
        PFN_vkCreateRenderPass CreateRenderPass{};
        PFN_vkDestroyRenderPass DestroyRenderPass{};
        PFN_vkCreateFramebuffer CreateFramebuffer{};
        PFN_vkDestroyFramebuffer DestroyFramebuffer{};
        PFN_vkCreateShaderModule CreateShaderModule{};
        PFN_vkDestroyShaderModule DestroyShaderModule{};
        PFN_vkCreatePipelineLayout CreatePipelineLayout{};
        PFN_vkDestroyPipelineLayout DestroyPipelineLayout{};
        PFN_vkCreateGraphicsPipelines CreateGraphicsPipelines{};
        PFN_vkDestroyPipeline DestroyPipeline{};
        PFN_vkCreateDescriptorSetLayout CreateDescriptorSetLayout{};
        PFN_vkDestroyDescriptorSetLayout DestroyDescriptorSetLayout{};
        PFN_vkCreateDescriptorPool CreateDescriptorPool{};
        PFN_vkDestroyDescriptorPool DestroyDescriptorPool{};
        PFN_vkAllocateDescriptorSets AllocateDescriptorSets{};
        PFN_vkUpdateDescriptorSets UpdateDescriptorSets{};
        PFN_vkCreateSampler CreateSampler{};
        PFN_vkDestroySampler DestroySampler{};
        PFN_vkCreateImageView CreateImageView{};
        PFN_vkDestroyImageView DestroyImageView{};
        PFN_vkCmdBeginRenderPass CmdBeginRenderPass{};
        PFN_vkCmdEndRenderPass CmdEndRenderPass{};
        PFN_vkCmdBindPipeline CmdBindPipeline{};
        PFN_vkCmdBindDescriptorSets CmdBindDescriptorSets{};
        PFN_vkCmdPushConstants CmdPushConstants{};
        PFN_vkCmdSetViewport CmdSetViewport{};
        PFN_vkCmdSetScissor CmdSetScissor{};
        PFN_vkCmdDraw CmdDraw{};
    };

    VkImageView colorView(const VulkanCtx& vk, VkImage image, VkFormat format);
    VkImageView depthView(const VulkanCtx& vk, VkImage image, VkFormat format);
    VkFramebuffer framebuffer(const VulkanCtx& vk, VkImage image);

    static const uint32_t kSets = 8;

    Fns m_f{};
    VkRenderPass m_renderPass{VK_NULL_HANDLE};
    VkDescriptorSetLayout m_setLayout{VK_NULL_HANDLE};
    VkPipelineLayout m_layout{VK_NULL_HANDLE};
    VkPipeline m_pipeline{VK_NULL_HANDLE};
    VkSampler m_colorSampler{VK_NULL_HANDLE};
    VkSampler m_depthSampler{VK_NULL_HANDLE};
    VkDescriptorPool m_pool{VK_NULL_HANDLE};
    VkDescriptorSet m_sets[kSets]{};
    uint32_t m_nextSet{0};
    std::map<VkImage, VkImageView> m_colorViews;
    std::map<VkImage, VkImageView> m_depthViews;
    std::map<VkImage, VkFramebuffer> m_framebuffers;
    VkFormat m_colorFormat{VK_FORMAT_UNDEFINED};
    uint32_t m_w{0}, m_h{0};
    bool m_ready{false};
};

} // namespace cemuvr
