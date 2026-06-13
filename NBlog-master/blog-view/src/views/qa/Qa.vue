<template>
	<div class="ui container m-top">
		<div class="m-deepseek-container">
			<!-- 左侧知识库和会话管理 -->
			<div class="m-deepseek-sidebar">
				<div class="m-deepseek-sidebar-header">
					<h3>知识库</h3>
				</div>
				
				<!-- 知识库列表 -->
				<div class="m-deepseek-sidebar-content">
					<KnowledgeBaseItem 
						v-for="kb in knowledgeBases" 
						:key="kb.id" 
						:kb="kb"
						@add-session="addSession"
						@edit-kb="showEditKbForm"
						@upload-document="uploadKnowledgeDocument"
						@delete-kb="handleDeleteKnowledgeBase"
						@load-sessions="loadKnowledgeBaseSessions"
					>
						<div class="m-deepseek-session-list" v-if="kb.sessions.length > 0">
							<SessionItem 
								v-for="(session, sessionIndex) in kb.sessions"
								:key="session.id"
								:session="session"
								:sessionIndex="sessionIndex"
								:kbId="kb.id"
								:activeSession="activeSession"
								@select-session="selectSession"
								@start-edit="startEditSession"
								@delete-session="handleDeleteSession"
							/>
						</div>
					</KnowledgeBaseItem>
				</div>
				
				<!-- 添加知识库按钮 -->
				<div class="m-deepseek-sidebar-footer">
					<button class="m-deepseek-btn m-deepseek-add-btn" @click="addKnowledgeBase">
						<i class="iconfont icon-add"></i> 新建知识库
					</button>
				</div>
			</div>
			
			<!-- 右侧对话区域 -->
			<div class="m-deepseek-main">
				<!-- 对话内容 -->
				<div class="m-deepseek-chat-area" ref="chatArea" @scroll="handleChatScroll">
					<div class="m-deepseek-message" v-for="(message, msgIndex) in messages" :key="msgIndex">
						<div class="m-deepseek-message-content" :class="{user: message.isUser}">
							<div class="m-deepseek-message-header" v-if="message.isUser">
								<span>我</span>
							</div>
							<div class="m-deepseek-message-header" v-else>
								<span>知识库</span>
							</div>
							<div class="m-deepseek-message-body" v-if="message.isUser || message.content">
								<span v-html="message.isUser ? message.content : parseMarkdown(message.content)"></span>
							</div>
							<div class="m-deepseek-message-body m-deepseek-thinking" v-else>
								<span class="m-thinking-text">思考中</span>
								<span class="m-thinking-dots">
									<span class="m-dot"></span>
									<span class="m-dot"></span>
									<span class="m-dot"></span>
								</span>
							</div>
						</div>
					</div>
				</div>
				
				<!-- 输入框 -->
				<div class="m-deepseek-input-area" v-if="activeSession">
					<div class="m-deepseek-input-container">
						<div class="m-deepseek-input-wrapper">
							<textarea 
								v-model="inputMessage" 
								placeholder="输入问题..." 
								@keydown.ctrl.enter.prevent="sendMessage"
								@keydown.meta.enter.prevent="sendMessage"
								class="m-deepseek-input"
								rows="3"
								:disabled="isGenerating"
								ref="messageInput"
							></textarea>
						</div>
						<button class="m-deepseek-send-btn" @click="sendMessage" :disabled="isGenerating">
							<span class="m-deepseek-send-btn-text">↑</span>
						</button>
					</div>
				</div>
			</div>
		</div>
		
		<!-- 确认删除弹框 -->
		<div class="m-delete-confirm" v-if="showConfirmDialog">
			<div class="m-delete-confirm-overlay"></div>
			<div class="m-delete-confirm-content">
				<h4>{{ confirmTitle }}</h4>
				<p>{{ confirmMessage }}</p>
				<div class="m-delete-confirm-actions">
					<button class="m-delete-confirm-btn m-delete-confirm-cancel" @click="cancelDelete">取消</button>
					<button class="m-delete-confirm-btn m-delete-confirm-confirm" @click="confirmDelete">确认删除</button>
				</div>
			</div>
		</div>
		
		<!-- 添加知识库表单弹框 -->
		<div class="m-add-kb-form" v-if="showAddKbForm">
			<div class="m-delete-confirm-overlay"></div>
			<div class="m-add-kb-form-content">
				<h4>添加知识库</h4>
				<div class="m-add-kb-form-body">
					<div class="m-form-item">
						<label class="m-form-label">知识库名称</label>
						<input type="text" v-model="kbForm.name" placeholder="请输入知识库名称" class="m-form-input">
					</div>
					<div class="m-form-item">
						<label class="m-form-label">知识库描述</label>
						<textarea v-model="kbForm.description" placeholder="请输入知识库描述" class="m-form-textarea" rows="3"></textarea>
					</div>
					<div class="m-form-item">
						<label class="m-form-label">知识库提示词</label>
						<textarea v-model="kbForm.prompt" placeholder="请输入知识库提示词" class="m-form-textarea" rows="4"></textarea>
					</div>
				</div>
				<div class="m-delete-confirm-actions">
					<button class="m-delete-confirm-btn m-delete-confirm-cancel" @click="cancelAddKbForm">取消</button>
					<button class="m-delete-confirm-btn m-delete-confirm-confirm" @click="submitAddKbForm">确认添加</button>
				</div>
			</div>
		</div>
		
		<!-- 修改知识库表单弹框 -->
		<div class="m-add-kb-form" v-if="showEditForm">
			<div class="m-delete-confirm-overlay"></div>
			<div class="m-add-kb-form-content">
				<h4>修改知识库</h4>
				<div class="m-add-kb-form-body">
					<div class="m-form-item">
						<label class="m-form-label">知识库名称</label>
						<input type="text" v-model="editKbForm.name" placeholder="请输入知识库名称" class="m-form-input">
					</div>
					<div class="m-form-item">
						<label class="m-form-label">知识库描述</label>
						<textarea v-model="editKbForm.description" placeholder="请输入知识库描述" class="m-form-textarea" rows="3"></textarea>
					</div>
					<div class="m-form-item">
						<label class="m-form-label">知识库提示词</label>
						<textarea v-model="editKbForm.prompt" placeholder="请输入知识库提示词" class="m-form-textarea" rows="4"></textarea>
					</div>
				</div>
				<div class="m-delete-confirm-actions">
					<button class="m-delete-confirm-btn m-delete-confirm-cancel" @click="cancelEditKbForm">取消</button>
					<button class="m-delete-confirm-btn m-delete-confirm-confirm" @click="submitEditKbForm">确认修改</button>
				</div>
			</div>
		</div>
		
		<!-- 修改会话名称表单弹框 -->
		<div class="m-add-kb-form" v-if="showEditSessionForm">
			<div class="m-delete-confirm-overlay"></div>
			<div class="m-add-kb-form-content">
				<h4>修改会话名称</h4>
				<div class="m-add-kb-form-body">
					<div class="m-form-item">
						<label class="m-form-label">会话名称</label>
						<input type="text" v-model="editSessionForm.name" placeholder="请输入会话名称" class="m-form-input">
					</div>
				</div>
				<div class="m-delete-confirm-actions">
					<button class="m-delete-confirm-btn m-delete-confirm-cancel" @click="cancelEditSessionForm">取消</button>
					<button class="m-delete-confirm-btn m-delete-confirm-confirm" @click="submitEditSessionForm">确认修改</button>
				</div>
			</div>
		</div>
		
		<!-- 上传文件对话框 -->
		<div class="m-add-kb-form" v-if="showUploadDialog">
			<div class="m-delete-confirm-overlay"></div>
			<div class="m-add-kb-form-content">
				<h4>上传知识文档</h4>
				
				<!-- 标签页 -->
				<div class="m-upload-tabs">
					<button 
						class="m-upload-tab" 
						:class="{ 'm-upload-tab-active': activeTab === 'upload' }"
						@click="activeTab = 'upload'"
					>
						上传文件
					</button>
					<button 
						class="m-upload-tab" 
						:class="{ 'm-upload-tab-active': activeTab === 'files' }"
						@click="switchToFilesTab"
					>
						已上传文件
					</button>
				</div>
				
				<div class="m-upload-dialog-body">
					<!-- 上传文件标签页 -->
					<div v-if="activeTab === 'upload'">
						<!-- 拖拽区域 -->
						<div 
							class="m-upload-dropzone" 
							:class="{ 'm-upload-dropzone-active': dragOver }"
							@dragover="handleDragOver"
							@dragleave="handleDragLeave"
							@drop="handleDrop"
						>
							<div class="m-upload-dropzone-content">
								<i class="m-upload-icon">📁</i>
								<p class="m-upload-text">拖入文件到此处，或</p>
								<label class="m-upload-btn">
									选择文件
									<input 
										type="file" 
										accept=".txt,.md,.pdf,.doc,.docx" 
										@change="handleFileSelect"
										style="display: none;"
									>
								</label>
								<p class="m-upload-hint">支持上传 txt、md、pdf、doc、docx 文件</p>
							</div>
						</div>
						
						<!-- 已选择文件列表 -->
						<div class="m-upload-file-list" v-if="uploadFiles.length > 0">
							<h5>已选择的文件</h5>
							<div class="m-upload-file-item" v-for="(file, index) in uploadFiles" :key="index">
								<span class="m-upload-file-name">{{ file.name }}</span>
								<span class="m-upload-file-size">{{ (file.size / 1024).toFixed(2) }} KB</span>
								<button class="m-upload-file-remove" @click="removeUploadFile(index)">×</button>
							</div>
						</div>
					</div>
					
					<!-- 已上传文件标签页 -->
					<div v-if="activeTab === 'files'">
						<div v-if="isLoadingFiles" class="m-upload-loading">
							加载中...
						</div>
						<div v-else-if="knowledgeFiles.length === 0" class="m-upload-empty">
							暂无已上传的文件
						</div>
						<div v-else class="m-upload-files-list">
							<div class="m-upload-files-header">
								<span>文件名</span>
								<span>大小</span>
								<span>类型</span>
								<span>状态</span>
							</div>
							<div class="m-upload-file-item" v-for="file in knowledgeFiles" :key="file.id">
								<span class="m-upload-file-name">{{ file.file_name }}</span>
								<span class="m-upload-file-size">{{ (file.file_size / 1024).toFixed(2) }} KB</span>
								<span class="m-upload-file-type">{{ file.file_type || '未知' }}</span>
								<span class="m-upload-file-status" :class="`m-upload-file-status-${file.status}`">
									{{ getStatusText(file.status) }}
								</span>
								<button class="m-upload-file-remove" @click="handleDeleteFile(file)" title="删除文件">×</button>
							</div>
						</div>
					</div>
				</div>
				<div class="m-delete-confirm-actions">
					<button class="m-delete-confirm-btn m-delete-confirm-cancel" @click="cancelUpload">取消</button>
					<button 
						class="m-delete-confirm-btn m-delete-confirm-confirm" 
						@click="startUpload"
						v-if="activeTab === 'upload'"
					>
						开始上传
					</button>
				</div>
			</div>
		</div>
		
		<!-- 页面内通知 -->
		<div class="m-alert-container" v-if="notificationVisible">
			<el-alert
				:title="notificationTitle"
				:type="notificationType"
				:description="notificationMessage"
				show-icon
				closable
				@close="closeNotification"
			/>
		</div>

	</div>
</template>

<script>
import KnowledgeBaseItem from '@/components/qa/KnowledgeBaseItem.vue';
import SessionItem from '@/components/qa/SessionItem.vue';
import { chatStream, getKnowledgeBases, getKnowledgeBaseSessions, createKnowledgeBase, deleteKnowledgeBase, updateKnowledgeBase, createSession, updateSession, deleteSession, createMessage, getSessionMessages, uploadKnowledgeFile, getKnowledgeFiles, deleteKnowledgeFile } from '@/api/qa';
import MarkdownIt from 'markdown-it';
import mk from '@iktakahiro/markdown-it-katex';
import 'katex/dist/katex.min.css';

export default {
  name: "Qa",
  components: {
    KnowledgeBaseItem,
    SessionItem
  },
  data() {
    return {
      md: new MarkdownIt({
        html: true,
        linkify: true,
        typographer: true,
        breaks: true
      }).use(mk),
      knowledgeBases: [],
      activeSession: null,
      messages: [],
      inputMessage: "",
      isGenerating: false,
      userHasScrolledUp: false,
      // 确认删除弹框相关
      showConfirmDialog: false,
      confirmType: '', // 'knowledgeBase' 或 'session'
      confirmId: null,
      kbId: null,
      confirmTitle: '',
      confirmMessage: '',
      // 添加知识库表单相关
      showAddKbForm: false,
      kbForm: {
        name: '',
        description: '',
        prompt: ''
      },
      // 修改知识库表单相关
      showEditForm: false,
      editKbId: null,
      editKbForm: {
        name: '',
        description: '',
        prompt: ''
      },
      // 上传文件对话框相关
      showUploadDialog: false,
      uploadKbId: null,
      uploadFiles: [],
      dragOver: false,
      activeTab: 'upload', // 'upload' 或 'files'
      knowledgeFiles: [],
      isLoadingFiles: false,
      // 提示信息对话框相关
      notificationVisible: false,
      notificationType: 'info', // 'success', 'error', 'info'
      notificationTitle: '',
      notificationMessage: '',
      // 修改会话名称表单相关
      showEditSessionForm: false,
      editSessionKbId: null,
      editSessionId: null,
      editSessionForm: {
        name: ''
      },
      // 删除知识文件相关
      deleteFileId: null,
      deleteFileName: ''
    }
  },
  mounted() {
    // 页面加载时获取知识库信息
    this.loadKnowledgeBases();
  },
  methods: {
    // 加载所有知识库信息
    async loadKnowledgeBases() {
      try {
        const response = await getKnowledgeBases();
        // 检查响应状态（更灵活的检查）
        if (!response || response.status < 200 || response.status >= 300) {
          throw new Error('获取知识库信息失败');
        }
        // 处理API响应，确保每个知识库对象都有sessions字段
        const data = response.data?.data || response.data || [];
        this.knowledgeBases = (data).map(kb => ({
          ...kb,
          prompt: kb.system_prompt || kb.prompt, // 兼容system_prompt字段
          sessions: kb.sessions || [] // 确保sessions字段存在
        }));
      } catch (error) {
        // 可以添加错误处理逻辑
      }
    },
    // 加载知识库下的会话信息
    async loadKnowledgeBaseSessions(kbId) {
      try {
        const response = await getKnowledgeBaseSessions(kbId);
        // 检查响应状态（更灵活的检查）
        if (!response || response.status < 200 || response.status >= 300) {
          throw new Error('获取会话信息失败');
        }
        const kb = this.knowledgeBases.find(k => k.id === kbId);
        if (kb) {
          // 处理API响应，确保sessions字段存在且格式正确
          const data = response.data?.data || response.data;
          if (Array.isArray(data)) {
            // 如果返回的是会话数组
            kb.sessions = data.map(session => ({
              id: session.id || crypto.randomUUID(),
              name: session.title || session.name,
              messages: session.messages || []
            }));
          } else if (data) {
            // 如果返回的是单个会话对象
            kb.sessions = [{
              id: data.id || crypto.randomUUID(),
              name: data.title || data.name,
              messages: data.messages || []
            }];
          } else {
            kb.sessions = [];
          }
        }
      } catch (error) {
        // 可以添加错误处理逻辑
      }
    },
    // 滚动到底部
    scrollToBottom() {
      this.$nextTick(() => {
        if (this.$refs.chatArea) {
          const chatArea = this.$refs.chatArea;
          const isAtBottom = chatArea.scrollHeight - chatArea.scrollTop - chatArea.clientHeight < 50;
          if (!this.userHasScrolledUp || !this.isGenerating) {
            chatArea.scrollTop = chatArea.scrollHeight;
          }
        }
      });
    },
    // 处理聊天区域滚动
    handleChatScroll() {
      if (this.$refs.chatArea) {
        const chatArea = this.$refs.chatArea;
        const isAtBottom = chatArea.scrollHeight - chatArea.scrollTop - chatArea.clientHeight < 50;
        this.userHasScrolledUp = !isAtBottom;
      }
    },
    // 显示添加知识库表单
    addKnowledgeBase() {
      // 重置表单
      this.kbForm = {
        name: '',
        description: '',
        prompt: ''
      };
      this.showAddKbForm = true;
    },
    // 提交添加知识库表单
    async submitAddKbForm() {
      if (!this.kbForm.name.trim()) {
        alert('请输入知识库名称');
        return;
      }
      
      try {
        // 调用API创建知识库
        const response = await createKnowledgeBase({
          name: this.kbForm.name,
          description: this.kbForm.description,
          system_prompt: this.kbForm.prompt
        });
        // 检查响应状态（更灵活的检查）
        if (!response || response.status < 200 || response.status >= 300) {
          throw new Error('创建知识库失败');
        }
        
        // 处理API响应，确保返回的知识库对象有sessions字段
        const data = response.data?.data || response.data;
        const newKb = {
          ...data,
          prompt: data.system_prompt || data.prompt,
          sessions: []
        };
        
        // 添加到知识库列表
        this.knowledgeBases.push(newKb);
        
        this.cancelAddKbForm();
      } catch (error) {
        alert('创建知识库失败，请稍后重试');
      }
    },
    // 取消添加知识库
    cancelAddKbForm() {
      this.showAddKbForm = false;
      this.kbForm = {
        name: '',
        description: '',
        prompt: ''
      };
    },
    // 显示修改知识库表单
    showEditKbForm(kb) {
      this.editKbId = kb.id;
      this.editKbForm = {
        name: kb.name,
        description: kb.description || '',
        prompt: kb.prompt || ''
      };
      this.showEditForm = true;
    },
    // 提交修改知识库表单
    async submitEditKbForm() {
      if (!this.editKbForm.name.trim()) {
        alert('请输入知识库名称');
        return;
      }
      
      try {
        // 调用API修改知识库
        const response = await updateKnowledgeBase(this.editKbId, {
          name: this.editKbForm.name,
          description: this.editKbForm.description,
          system_prompt: this.editKbForm.prompt
        });
        // 检查响应状态（更灵活的检查）
        if (!response || response.status < 200 || response.status >= 300) {
          throw new Error('修改知识库失败');
        }
        
        // 更新本地知识库信息
        const data = response.data?.data || response.data;
        const kb = this.knowledgeBases.find(k => k.id === this.editKbId);
        if (kb) {
          kb.name = data.name;
          kb.description = data.description;
          kb.prompt = data.system_prompt || data.prompt;
        }
        
        this.cancelEditKbForm();
      } catch (error) {
        alert('修改知识库失败，请稍后重试');
      }
    },
    // 取消修改知识库
    cancelEditKbForm() {
      this.showEditForm = false;
      this.editKbId = null;
      this.editKbForm = {
        name: '',
        description: '',
        prompt: ''
      };
    },
    // 显示上传文件对话框
    uploadKnowledgeDocument(kbId) {
      this.uploadKbId = kbId;
      this.uploadFiles = [];
      this.activeTab = 'upload';
      this.knowledgeFiles = [];
      this.showUploadDialog = true;
    },
    // 切换到已上传文件标签页
    async switchToFilesTab() {
      this.activeTab = 'files';
      await this.loadKnowledgeFiles();
    },
    // 加载知识库文件列表
    async loadKnowledgeFiles() {
      if (!this.uploadKbId) return;
      
      this.isLoadingFiles = true;
      try {
        const response = await getKnowledgeFiles(this.uploadKbId);
        if (response && (response.status >= 200 && response.status < 300 || response.code === '200')) {
          const data = response.data?.data || response.data || [];
          this.knowledgeFiles = data;
        }
      } catch (error) {
        this.showNotification('error', '加载失败', '获取文件列表失败，请稍后重试');
      } finally {
        this.isLoadingFiles = false;
      }
    },
    // 获取状态文本
    getStatusText(status) {
      const statusMap = {
        'pending': '待处理',
        'processing': '处理中',
        'completed': '已完成',
        'failed': '失败'
      };
      return statusMap[status] || status;
    },
    // 处理知识文件删除点击
    handleDeleteFile(file) {
      this.deleteFileId = file.id;
      this.deleteFileName = file.file_name;
      this.showDeleteConfirm('knowledgeFile', file.id, file.file_name);
    },
    // 执行删除知识文件
    async deleteKnowledgeFileById(fileId) {
      try {
        const response = await deleteKnowledgeFile(fileId);
        if (!response || response.status < 200 || response.status >= 300) {
          throw new Error('删除文件失败');
        }
        // 从本地列表中移除
        const index = this.knowledgeFiles.findIndex(f => f.id === fileId);
        if (index > -1) {
          this.knowledgeFiles.splice(index, 1);
        }
      } catch (error) {
        alert('删除文件失败，请稍后重试');
      }
    },
    // 显示提示信息
    showNotification(type, title, message) {
      this.notificationType = type;
      this.notificationTitle = title;
      this.notificationMessage = message;
      this.notificationVisible = true;
      
      // 3秒后自动关闭
      setTimeout(() => {
        this.closeNotification();
      }, 3000);
    },
    // 关闭提示信息
    closeNotification() {
      this.notificationVisible = false;
      // 延迟清空内容，确保动画完成
      setTimeout(() => {
        this.notificationType = 'info';
        this.notificationTitle = '';
        this.notificationMessage = '';
      }, 300);
    },
    // 处理文件拖入
    handleDragOver(e) {
      e.preventDefault();
      this.dragOver = true;
    },
    // 处理文件拖离
    handleDragLeave(e) {
      e.preventDefault();
      this.dragOver = false;
    },
    // 处理文件放置
    handleDrop(e) {
      e.preventDefault();
      this.dragOver = false;
      
      const files = e.dataTransfer.files;
      if (files.length > 0) {
        // 只处理第一个文件
        this.addFile(files[0]);
      }
    },
    // 处理文件选择
    handleFileSelect(e) {
      const files = e.target.files;
      if (files.length > 0) {
        // 只处理第一个文件
        this.addFile(files[0]);
      }
    },
    // 添加单个文件到上传列表
    addFile(file) {
      // 清空现有文件列表
      this.uploadFiles = [];
      // 检查文件大小（50MB）
      const maxSize = 50 * 1024 * 1024; // 50MB
      if (file.size > maxSize) {
        this.showNotification('error', '上传失败', `文件 ${file.name} 大小超过50MB，不支持上传`);
        return;
      }
      // 检查文件类型
      const allowedTypes = ['.txt', '.md', '.pdf', '.doc', '.docx'];
      const fileExtension = '.' + file.name.split('.').pop().toLowerCase();
      if (allowedTypes.includes(fileExtension)) {
        this.uploadFiles.push(file);
      } else {
        this.showNotification('error', '上传失败', `文件 ${file.name} 类型不支持，请上传 txt、md、pdf、doc 或 docx 文件`);
      }
    },
    // 移除上传文件
    removeUploadFile(index) {
      this.uploadFiles.splice(index, 1);
    },
    // 开始上传文件
    async startUpload() {
      if (this.uploadFiles.length === 0) {
        this.showNotification('info', '提示', '请先选择要上传的文件');
        return;
      }
      
      let successCount = 0;
      let hasError = false;
      
      try {
        // 逐个上传文件
        for (let i = 0; i < this.uploadFiles.length; i++) {
          const file = this.uploadFiles[i];
          try {
            const response = await uploadKnowledgeFile(this.uploadKbId, file);
            successCount++;
          } catch (fileError) {
            // 处理单个文件上传错误
            const errorMessage = fileError.response?.data?.detail || fileError.message || '文件上传失败';
            this.showNotification('error', '上传失败', `文件 ${file.name} 上传失败: ${errorMessage}`);
            hasError = true;
            // 继续上传其他文件
            continue;
          }
        }
        
        if (successCount > 0) {
          this.showNotification('success', '上传成功', `成功上传 ${successCount} 个文件到知识库`);
          // 上传完成后刷新知识库列表
          this.loadKnowledgeBases();
          // 如果当前在已上传文件标签页，重新加载文件列表
          if (this.activeTab === 'files') {
            await this.loadKnowledgeFiles();
          }
          // 只有在上传成功时才关闭对话框
          this.cancelUpload();
        }
      } catch (error) {
        const errorMessage = error.response?.data?.detail || error.message || '文件上传失败';
        this.showNotification('error', '上传失败', `文件上传失败: ${errorMessage}`);
        hasError = true;
        // 错误时不关闭对话框
      }
    },
    // 取消上传
    cancelUpload() {
      this.showUploadDialog = false;
      // 保留uploadKbId，以便在需要时重新加载文件列表
      // this.uploadKbId = null;
      this.uploadFiles = [];
      this.dragOver = false;
    },
    // 开始编辑会话名称（显示表单弹窗）
    startEditSession(kbId, sessionId) {
      const kb = this.knowledgeBases.find(k => k.id === kbId);
      if (kb) {
        const session = kb.sessions.find(s => s.id === sessionId);
        if (session) {
          // 获取会话索引
          const sessionIndex = kb.sessions.findIndex(s => s.id === sessionId);
          // 保存编辑会话信息
          this.editSessionKbId = kbId;
          this.editSessionId = sessionId;
          // 设置表单数据（类似于知识库编辑逻辑）
          this.editSessionForm.name = session.name || `会话 ${sessionIndex + 1}`;
          // 显示编辑表单弹窗
          this.showEditSessionForm = true;
        }
      }
    },
    // 提交修改会话名称表单
    async submitEditSessionForm() {
      const kb = this.knowledgeBases.find(k => k.id === this.editSessionKbId);
      if (kb) {
        const sessionIndex = kb.sessions.findIndex(s => s.id === this.editSessionId);
        if (sessionIndex > -1) {
          const newName = this.editSessionForm.name.trim();
          try {
            // 调用API更新会话名称
            const response = await updateSession(this.editSessionId, {
              title: newName || null
            });
            // 检查响应状态（更灵活的检查）
            if (!response || response.status < 200 || response.status >= 300) {
              throw new Error('更新会话失败');
            }
            // 使用Vue.set确保响应式更新
            this.$set(kb.sessions[sessionIndex], 'name', newName || null);
          } catch (error) {
            // 出错时仍然更新本地状态
            this.$set(kb.sessions[sessionIndex], 'name', newName || null);
          }
        }
      }
      // 关闭表单并重置数据
      this.cancelEditSessionForm();
    },
    // 取消修改会话名称表单
    cancelEditSessionForm() {
      this.showEditSessionForm = false;
      this.editSessionKbId = null;
      this.editSessionId = null;
      this.editSessionForm.name = '';
    },
    // 添加会话
    async addSession(kbId) {
      const kb = this.knowledgeBases.find(k => k.id === kbId);
      if (kb) {
        try {
          // 调用API创建会话，默认标题为"新会话"
          const response = await createSession({
            knowledge_base_id: kbId,
            title: "新会话"
          });
          // 检查响应状态（更灵活的检查）
          if (!response || response.status < 200 || response.status >= 300) {
            throw new Error('创建会话失败');
          }
          
          // 处理API响应，确保返回的会话对象格式正确
          const data = response.data?.data || response.data;
          const newSession = {
            id: data.id || crypto.randomUUID(),
            name: data.title || data.name || "新会话",
            messages: data.messages || []
          };
          
          // 添加到会话列表
          kb.sessions.push(newSession);
          this.selectSession(newSession.id);
        } catch (error) {
          // 出错时使用本地创建作为后备
          const newSessionId = crypto.randomUUID();
          kb.sessions.push({id: newSessionId, name: "新会话", messages: []});
          this.selectSession(newSessionId);
        }
      }
    },
    // 显示确认删除弹框
    showDeleteConfirm(type, id, name, sessionNum) {
      this.confirmType = type;
      this.confirmId = id;

      if (type === 'knowledgeBase') {
        this.confirmTitle = '确认删除知识库';
        this.confirmMessage = `确定要删除知识库 "${name}" 吗？删除后将同时删除该知识库下的所有会话。`;
      } else if (type === 'session') {
        this.kbId = id;
        this.confirmId = name; // sessionId
        this.confirmTitle = '确认删除会话';
        this.confirmMessage = `确定要删除 "会话 ${sessionNum}" 吗？删除后会话中的所有消息将被清除。`;
      } else if (type === 'knowledgeFile') {
        this.confirmTitle = '确认删除知识文件';
        this.confirmMessage = `确定要删除文件 "${name}" 吗？`;
      }

      this.showConfirmDialog = true;
    },
    // 取消删除
    cancelDelete() {
      this.showConfirmDialog = false;
      this.confirmType = '';
      this.confirmId = null;
      this.kbId = null;
      this.confirmTitle = '';
      this.confirmMessage = '';
      this.deleteFileId = null;
      this.deleteFileName = '';
    },
    // 确认删除
    async confirmDelete() {
      if (this.confirmType === 'knowledgeBase') {
        await this.deleteKnowledgeBase(this.confirmId);
      } else if (this.confirmType === 'session') {
        await this.deleteSession(this.kbId, this.confirmId);
      } else if (this.confirmType === 'knowledgeFile') {
        await this.deleteKnowledgeFileById(this.confirmId);
      }
      this.cancelDelete();
    },
    // 删除知识库
    async deleteKnowledgeBase(kbId) {
      try {
        // 调用API删除知识库
        const response = await deleteKnowledgeBase(kbId);
        // 检查响应状态（更灵活的检查）
        if (!response || response.status < 200 || response.status >= 300) {
          throw new Error('删除知识库失败');
        }
        
        // API调用成功后，从本地列表中删除
        const index = this.knowledgeBases.findIndex(k => k.id === kbId);
        if (index > -1) {
          this.knowledgeBases.splice(index, 1);
          // 如果删除的知识库包含当前活跃会话，清空活跃会话
          if (this.activeSession) {
            let sessionExists = false;
            for (let kb of this.knowledgeBases) {
              if (kb.sessions.some(s => s.id === this.activeSession)) {
                sessionExists = true;
                break;
              }
            }
            if (!sessionExists) {
              this.activeSession = null;
              this.messages = [];
            }
          }
        }
      } catch (error) {
        alert('删除知识库失败，请稍后重试');
      }
    },
    // 删除会话
    async deleteSession(kbId, sessionId) {
      const kb = this.knowledgeBases.find(k => k.id === kbId);
      if (kb) {
        const sessionIndex = kb.sessions.findIndex(s => s.id === sessionId);
        if (sessionIndex > -1) {
          try {
            // 调用API删除会话
            const response = await deleteSession(sessionId);
            // 检查响应状态（更灵活的检查）
            if (!response || response.status < 200 || response.status >= 300) {
              throw new Error('删除会话失败');
            }
          } catch (error) {
            // 出错时仍然删除本地会话
          }
          kb.sessions.splice(sessionIndex, 1);
          // 如果删除的是当前活跃会话，清空活跃会话
          if (this.activeSession === sessionId) {
            this.activeSession = null;
            this.messages = [];
          }
        }
      }
    },
    // 选择会话
    async selectSession(sessionId) {
      this.activeSession = sessionId;
      try{
			// 从服务器加载会话历史消息
			const response = await getSessionMessages(sessionId);
			// 检查响应是否成功，适配后端返回的格式 {code: '200', data: [...]}
			if (response && (response.status >= 200 && response.status < 300 || response.code === '200')) {
			const data = response.data?.data || response.data || [];
			// 转换消息格式，适配前端显示
			this.messages = data.map(msg => ({
				isUser: msg.role === 'user',
				content: msg.content
			}));

			// 更新本地会话消息
			for (let kb of this.knowledgeBases) {
				const session = kb.sessions.find(s => s.id === sessionId);
				if (session) {
				session.messages = this.messages;
				break;
				}
			}
			// 滚动到底部显示最新消息
			this.$nextTick(() => this.scrollToBottom());
			return;
			}
      } catch (error) {
      }

      // 如果加载失败，使用本地存储的消息
      for (let kb of this.knowledgeBases) {
        const session = kb.sessions.find(s => s.id === sessionId);
        if (session) {
          this.messages = session.messages;
          // 滚动到底部显示最新消息
          this.$nextTick(() => this.scrollToBottom());
          return;
        }
      }
      this.messages = [];
    },
    // 发送消息
    async sendMessage() {
      if (!this.inputMessage.trim()) return;
      
      // 保存用户输入的问题
      const question = this.inputMessage;
      
      // 如果没有活跃会话，自动选择第一个可用会话或创建新会话
      if (!this.activeSession) {
        // 查找第一个有会话的知识库
        let targetKb = null;
        for (let kb of this.knowledgeBases) {
          if (kb.sessions.length > 0) {
            targetKb = kb;
            break;
          }
        }
        
        // 如果没有会话，创建一个新会话
        if (!targetKb) {
          // 如果没有知识库，创建一个新知识库
          if (this.knowledgeBases.length === 0) {
            const newKbId = 1;
            this.knowledgeBases.push({
              id: newKbId,
              name: "默认知识库",
              description: "",
              prompt: "",
              sessions: []
            });
            targetKb = this.knowledgeBases[0];
          } else {
            targetKb = this.knowledgeBases[0];
          }
        }
        
        // 在目标知识库中创建新会话
        try {
          // 调用API创建会话，默认标题为"新会话"
          const response = await createSession({
            knowledge_base_id: targetKb.id,
            title: "新会话"
          });
          // 检查响应状态（更灵活的检查）
          if (!response || response.status < 200 || response.status >= 300) {
            throw new Error('创建会话失败');
          }
          
          // 处理API响应，确保返回的会话对象格式正确
          const data = response.data?.data || response.data;
          const newSession = {
            id: data.id || crypto.randomUUID(),
            name: data.title || data.name || "新会话",
            messages: data.messages || []
          };
          targetKb.sessions.push(newSession);
          this.activeSession = newSession.id;
          this.messages = [];
        } catch (error) {
          // 出错时使用本地创建作为后备
          const newSessionId = crypto.randomUUID();
          targetKb.sessions.push({id: newSessionId, name: "新会话", messages: []});
          this.activeSession = newSessionId;
          this.messages = [];
        }
      }
      
      // 查找当前活跃会话所属的知识库ID
      let currentKbId = null;
      for (let kb of this.knowledgeBases) {
        const session = kb.sessions.find(s => s.id === this.activeSession);
        if (session) {
          currentKbId = kb.id;
          break;
        }
      }

      // 添加用户消息
      const userMessage = {
        isUser: true,
        content: question
      };
      this.messages.push(userMessage);
      this.scrollToBottom();

      // 保存用户消息到数据库
      if (currentKbId) {
        createMessage({
          knowledge_base_id: currentKbId,
          role: 'user',
          content: question,
          session_id: this.activeSession
        }).catch(() => {
        });
      }

      // 创建AI回复消息占位
      const aiMessage = {
        isUser: false,
        content: ''
      };
      this.messages.push(aiMessage);

      // 保存到对应会话
      for (let kb of this.knowledgeBases) {
        const session = kb.sessions.find(s => s.id === this.activeSession);
        if (session) {
          session.messages = [...this.messages];
          break;
        }
      }

      // 调用API获取回复
      this.isGenerating = true;
      this.userHasScrolledUp = false;
      this.callChatApi(question, aiMessage, currentKbId);

      // 清空输入框
      this.inputMessage = "";
    },
    // 调用聊天API
    async callChatApi(question, aiMessage, currentKbId) {
      try {
        let historyMessages = [];
        for (const m of this.messages) {
          if (m === aiMessage) continue;
          if (!m.content) continue;
          historyMessages.push({
            role: m.isUser ? "user" : "assistant",
            content: m.content
          });
        }
        historyMessages = historyMessages.slice(historyMessages.length - 100, historyMessages.length);
        historyMessages = JSON.stringify(historyMessages)
        await chatStream(question, currentKbId,historyMessages, (res) => {
          console.log(res.message)
          aiMessage.content += res.message;
          this.$forceUpdate();
          this.scrollToBottom();
        });

        // 保存AI回复到数据库
        if (currentKbId) {
          createMessage({
            knowledge_base_id: currentKbId,
            role: 'assistant',
            content: aiMessage.content,
            session_id: this.activeSession
          }).catch(() => {
          });
        }

        // 更新会话消息
        for (let kb of this.knowledgeBases) {
          const session = kb.sessions.find(s => s.id === this.activeSession);
          if (session) {
            session.messages = [...this.messages];
            break;
          }
        }

        this.isGenerating = false;
        this.$nextTick(() => {
          this.$refs.messageInput?.focus();
        });
      } catch (error) {
        // 更新原有的AI回复占位符为错误消息
        aiMessage.content = '抱歉，发生了错误，请稍后再试。';
        this.$forceUpdate();
        this.scrollToBottom();
        this.isGenerating = false;
        
        // 保存错误消息到数据库
        if (currentKbId) {
          createMessage({
            knowledge_base_id: currentKbId,
            role: 'assistant',
            content: aiMessage.content,
            session_id: this.activeSession
          }).catch(() => {
          });
        }

        // 更新会话消息
        for (let kb of this.knowledgeBases) {
          const session = kb.sessions.find(s => s.id === this.activeSession);
          if (session) {
            session.messages = [...this.messages];
            break;
          }
        }
      }
    },
    // 处理知识库删除事件
    handleDeleteKnowledgeBase(kbId, kbName) {
      this.showDeleteConfirm('knowledgeBase', kbId, kbName);
    },
    // 处理会话删除事件
    handleDeleteSession(kbId, sessionId, sessionName) {
      this.showDeleteConfirm('session', kbId, sessionId, sessionName);
    },
    // 解析Markdown文本
    parseMarkdown(text) {
      // 将 LaTeX 标准语法转换为 katex 插件支持的语法
      // \[...\] → $$...$$ (display math)
      // \(...\) → $...$ (inline math)
      let processedText = text
        .replace(/\\\[/g, '$$$$')
        .replace(/\\\]/g, '$$$$')
        .replace(/\\\(/g, '$')
        .replace(/\\\)/g, '$');
      return this.md.render(processedText);
    }
  }
}
</script>

<style scoped>
.m-top {
	margin-top: 20px;
	margin-bottom: 20px;
}

/* DeepSeek风格容器 */
.m-deepseek-container {
	display: flex;
	height: 85vh;
	border: 1px solid #e1e5e9;
	border-radius: 8px;
	overflow: hidden;
	box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

/* 左侧边栏 */
.m-deepseek-sidebar {
	width: 300px;
	background-color: #f8f9fa;
	border-right: 1px solid #e1e5e9;
	display: flex;
	flex-direction: column;
}

.m-deepseek-sidebar-header {
	padding: 20px;
	border-bottom: 1px solid #e1e5e9;
	background-color: #ffffff;
}

.m-deepseek-sidebar-header h3 {
	margin: 0;
	font-size: 16px;
	font-weight: 600;
	color: #333;
}

.m-deepseek-sidebar-content {
	flex: 1;
	overflow-y: auto;
	padding: 10px;
}
/* 发送按钮文字 */
.m-deepseek-send-btn-text {
	font-size: 14px;
	font-weight: 500;
}

.m-deepseek-session-list {
	margin-left: 10px;
}




.m-deepseek-sidebar-footer {
	padding: 15px;
	border-top: 1px solid #e1e5e9;
	background-color: #ffffff;
}

.m-deepseek-add-btn {
	width: 100%;
	justify-content: center;
	gap: 8px;
	color: #2196f3;
	border: 1px solid #2196f3;
	padding: 10px;
	border-radius: 6px;
	font-size: 14px;
	font-weight: 500;
}

.m-deepseek-add-btn:hover {
	background-color: #e3f2fd;
}

/* 右侧主区域 */
.m-deepseek-main {
	flex: 1;
	display: flex;
	flex-direction: column;
	background-color: #ffffff;
}

.m-deepseek-chat-area {
	flex: 1;
	overflow-y: auto;
	padding: 20px;
}

.m-deepseek-message {
	margin-bottom: 20px;
}

.m-deepseek-message-content {
	max-width: 80%;
}

.m-deepseek-message-content.user {
	margin-left: auto;
}

.m-deepseek-message-header {
	font-size: 12px;
	color: #666;
	margin-bottom: 6px;
}

.m-deepseek-message-body {
	padding: 12px 16px;
	border-radius: 8px;
	line-height: 1.5;
}

.m-deepseek-message-content:not(.user) .m-deepseek-message-body {
	background-color: #f1f3f4;
	color: #333;
	border-bottom-left-radius: 2px;
}

.m-deepseek-message-content.user .m-deepseek-message-body {
		background-color: #2196f3;
		color: #ffffff;
		border-bottom-right-radius: 2px;
	}

	/* 思考中动画样式 */
	.m-deepseek-thinking {
		display: flex;
		align-items: center;
		gap: 4px;
	}

	.m-thinking-text {
		color: #666;
	}

	.m-thinking-dots {
		display: inline-flex;
		gap: 3px;
	}

	.m-dot {
		width: 6px;
		height: 6px;
		background-color: #666;
		border-radius: 50%;
		animation: m-dot-bounce 1.4s infinite ease-in-out both;
	}

	.m-dot:nth-child(1) {
		animation-delay: -0.32s;
	}

	.m-dot:nth-child(2) {
		animation-delay: -0.16s;
	}

	.m-dot:nth-child(3) {
		animation-delay: 0s;
	}

	@keyframes m-dot-bounce {
		0%, 80%, 100% {
			transform: scale(0.6);
			opacity: 0.4;
		}
		40% {
			transform: scale(1);
			opacity: 1;
		}
	}

	/* Markdown样式 */
	.m-deepseek-message-body h1, .m-deepseek-message-body h2, .m-deepseek-message-body h3, .m-deepseek-message-body h4, .m-deepseek-message-body h5, .m-deepseek-message-body h6 {
		margin: 10px 0;
		font-weight: 600;
	}

	.m-deepseek-message-body h1 {
		font-size: 20px;
	}

	.m-deepseek-message-body h2 {
		font-size: 18px;
	}

	.m-deepseek-message-body h3 {
		font-size: 16px;
	}

	.m-deepseek-message-body ul, .m-deepseek-message-body ol {
		margin: 10px 0;
		padding-left: 20px;
	}

	.m-deepseek-message-body li {
		margin: 5px 0;
	}

	.m-deepseek-message-body code {
		background-color: rgba(0, 0, 0, 0.05);
		padding: 2px 4px;
		border-radius: 3px;
		font-family: 'Courier New', Courier, monospace;
		font-size: 14px;
	}

	.m-deepseek-message-body pre {
		background-color: rgba(0, 0, 0, 0.05);
		padding: 10px;
		border-radius: 5px;
		overflow-x: auto;
		margin: 10px 0;
	}

	.m-deepseek-message-body pre code {
		background-color: transparent;
		padding: 0;
	}

	.m-deepseek-message-body blockquote {
		border-left: 4px solid #2196f3;
		padding-left: 10px;
		margin: 10px 0;
		color: #666;
	}

	.m-deepseek-message-body a {
		color: #2196f3;
		text-decoration: none;
	}

	.m-deepseek-message-body a:hover {
		text-decoration: underline;
	}

/* 输入区域 */
.m-deepseek-input-area {
	padding: 20px;
	border-top: 1px solid #e1e5e9;
	background-color: #f8f9fa;
}

.m-deepseek-input-container {
	display: flex;
	gap: 10px;
	align-items: flex-end;
}

.m-deepseek-input-wrapper {
	flex: 1;
	border: 1px solid #e1e5e9;
	border-radius: 8px;
	background-color: #ffffff;
	overflow: hidden;
}

.m-deepseek-input-header {
	padding: 8px 12px;
	border-bottom: 1px solid #f0f0f0;
	background-color: #fafafa;
}


.m-deepseek-input {
	width: 100%;
	padding: 12px 16px;
	border: none;
	resize: none;
	font-size: 14px;
	line-height: 1.5;
	background-color: #ffffff;
	min-height: 80px;
	max-height: 200px;
	overflow-y: auto;
}

.m-deepseek-input:focus {
	outline: none;
}

.m-deepseek-send-btn {
	background-color: #2196f3;
	color: #ffffff;
	border: none;
	border-radius: 50%;
	width: 40px;
	height: 40px;
	cursor: pointer;
	display: flex;
	align-items: center;
	justify-content: center;
	transition: background-color 0.2s ease;
	flex-shrink: 0;
}

.m-deepseek-send-btn:hover {
	background-color: #1976d2;
}

.m-deepseek-send-btn:disabled {
	background-color: #bdbdbd;
	cursor: not-allowed;
}

.m-deepseek-send-btn-text {
	font-size: 16px;
	font-weight: bold;
}

/* 滚动条样式 */
.m-deepseek-sidebar-content::-webkit-scrollbar,
.m-deepseek-chat-area::-webkit-scrollbar,
.m-deepseek-input::-webkit-scrollbar {
	width: 6px;
}

.m-deepseek-sidebar-content::-webkit-scrollbar-track,
.m-deepseek-chat-area::-webkit-scrollbar-track,
.m-deepseek-input::-webkit-scrollbar-track {
	background: #f1f1f1;
	border-radius: 3px;
}

.m-deepseek-sidebar-content::-webkit-scrollbar-thumb,
.m-deepseek-chat-area::-webkit-scrollbar-thumb,
.m-deepseek-input::-webkit-scrollbar-thumb {
	background: #c1c1c1;
	border-radius: 3px;
}

.m-deepseek-sidebar-content::-webkit-scrollbar-thumb:hover,
.m-deepseek-chat-area::-webkit-scrollbar-thumb:hover,
.m-deepseek-input::-webkit-scrollbar-thumb:hover {
	background: #a8a8a8;
}

/* 响应式设计 */
@media (max-width: 768px) {
	.m-deepseek-container {
		flex-direction: column;
		height: 90vh;
	}
	
	.m-deepseek-sidebar {
		width: 100%;
		height: 30%;
		border-right: none;
		border-bottom: 1px solid #e1e5e9;
	}
	
	.m-deepseek-main {
		height: 70%;
	}
	
	.m-deepseek-message-content {
		max-width: 90%;
	}
}

/* 确认删除弹框样式 */
.m-delete-confirm {
	position: fixed;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	z-index: 1100;
	display: flex;
	align-items: center;
	justify-content: center;
}

.m-delete-confirm-overlay {
	position: absolute;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	background-color: rgba(0, 0, 0, 0.5);
	backdrop-filter: blur(2px);
}

.m-delete-confirm-content {
	position: relative;
	background-color: #ffffff;
	border-radius: 8px;
	padding: 24px;
	width: 90%;
	max-width: 400px;
	box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
	z-index: 1001;
}

.m-delete-confirm-content h4 {
	margin: 0 0 12px 0;
	font-size: 18px;
	font-weight: 600;
	color: #333;
}

.m-delete-confirm-content p {
	margin: 0 0 24px 0;
	font-size: 14px;
	color: #666;
	line-height: 1.5;
}

.m-delete-confirm-actions {
	display: flex;
	justify-content: flex-end;
	gap: 12px;
}

.m-delete-confirm-btn {
	padding: 8px 16px;
	border-radius: 4px;
	font-size: 14px;
	font-weight: 500;
	cursor: pointer;
	transition: all 0.2s ease;
	border: 1px solid transparent;
}

.m-delete-confirm-cancel {
	background-color: #f8f9fa;
	color: #333;
	border-color: #e1e5e9;
}

.m-delete-confirm-cancel:hover {
	background-color: #e9ecef;
}

.m-delete-confirm-confirm {
	background-color: #dc3545;
	color: #ffffff;
}

.m-delete-confirm-confirm:hover {
	background-color: #c82333;
}

/* 响应式弹框 */
@media (max-width: 480px) {
	.m-delete-confirm-content {
		padding: 20px;
		width: 95%;
	}
	
	.m-delete-confirm-content h4 {
		font-size: 16px;
	}
	
	.m-delete-confirm-content p {
		font-size: 13px;
	}
	
	.m-delete-confirm-btn {
		padding: 6px 12px;
		font-size: 13px;
	}
}

/* 添加知识库表单样式 */
.m-add-kb-form {
	position: fixed;
	top: 0;
	left: 0;
	width: 100%;
	height: 100%;
	z-index: 1000;
	display: flex;
	align-items: center;
	justify-content: center;
}

.m-add-kb-form-content {
	position: relative;
	background-color: #ffffff;
	border-radius: 8px;
	padding: 24px;
	width: 90%;
	max-width: 600px;
	box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
	z-index: 1001;
}

.m-add-kb-form-content h4 {
	margin: 0 0 20px 0;
	font-size: 18px;
	font-weight: 600;
	color: #333;
}

.m-add-kb-form-body {
	margin-bottom: 24px;
}

.m-form-item {
	margin-bottom: 16px;
}

.m-form-label {
	display: block;
	margin-bottom: 8px;
	font-size: 14px;
	font-weight: 500;
	color: #333;
}

.m-form-input,
.m-form-textarea {
	width: 100%;
	padding: 10px 12px;
	border: 1px solid #e1e5e9;
	border-radius: 4px;
	font-size: 14px;
	transition: border-color 0.2s ease;
	box-sizing: border-box;
}

.m-form-input:focus,
.m-form-textarea:focus {
	outline: none;
	border-color: #2196f3;
	box-shadow: 0 0 0 2px rgba(33, 150, 243, 0.1);
}

.m-form-textarea {
	resize: vertical;
	min-height: 80px;
}

/* 响应式添加知识库表单 */
@media (max-width: 480px) {
	.m-add-kb-form-content {
		padding: 20px;
		width: 95%;
	}
	
	.m-add-kb-form-content h4 {
		font-size: 16px;
		margin-bottom: 16px;
	}
	
	.m-form-item {
		margin-bottom: 12px;
	}
	
	.m-form-label {
		font-size: 13px;
		margin-bottom: 6px;
	}
	
	.m-form-input,
	.m-form-textarea {
		padding: 8px 10px;
		font-size: 13px;
	}
}

/* 上传文件对话框样式 */
.m-upload-dialog-body {
	margin-bottom: 24px;
}

/* 标签页样式 */
.m-upload-tabs {
	display: flex;
	margin-bottom: 20px;
	border-bottom: 1px solid #e1e5e9;
}

.m-upload-tab {
	flex: 1;
	padding: 10px;
	background: none;
	border: none;
	font-size: 14px;
	font-weight: 500;
	color: #666;
	cursor: pointer;
	transition: all 0.2s ease;
	border-bottom: 2px solid transparent;
}

.m-upload-tab:hover {
	color: #2196f3;
}

.m-upload-tab-active {
	color: #2196f3 !important;
	border-bottom-color: #2196f3 !important;
}

.m-upload-dropzone {
	border: 2px dashed #e1e5e9;
	border-radius: 8px;
	padding: 40px 20px;
	text-align: center;
	transition: all 0.3s ease;
	margin-bottom: 20px;
}

.m-upload-dropzone-active {
	border-color: #2196f3;
	background-color: rgba(33, 150, 243, 0.05);
}

.m-upload-dropzone-content {
	display: flex;
	flex-direction: column;
	align-items: center;
	gap: 12px;
}

.m-upload-icon {
	font-size: 48px;
}

.m-upload-text {
	font-size: 16px;
	color: #333;
	margin: 0;
}

.m-upload-btn {
	background-color: #2196f3;
	color: #ffffff;
	padding: 8px 16px;
	border-radius: 4px;
	cursor: pointer;
	font-size: 14px;
	font-weight: 500;
	transition: background-color 0.2s ease;
}

.m-upload-btn:hover {
	background-color: #1976d2;
}

.m-upload-hint {
	font-size: 12px;
	color: #999;
	margin: 0;
}

.m-upload-file-list {
	margin-top: 20px;
}

.m-upload-file-list h5 {
	margin: 0 0 12px 0;
	font-size: 14px;
	font-weight: 600;
	color: #333;
}

/* 已上传文件列表样式 */
.m-upload-loading {
	text-align: center;
	padding: 40px;
	color: #666;
}

.m-upload-empty {
	text-align: center;
	padding: 40px;
	color: #999;
}

.m-upload-files-list {
	border: 1px solid #e1e5e9;
	border-radius: 4px;
	overflow: hidden;
}

.m-upload-files-header {
	display: flex;
	background-color: #f8f9fa;
	padding: 10px;
	border-bottom: 1px solid #e1e5e9;
	font-weight: 600;
	font-size: 14px;
}

.m-upload-files-header span {
	flex: 1;
}

.m-upload-file-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 10px;
	border: 1px solid #e1e5e9;
	border-radius: 4px;
	margin-bottom: 8px;
	background-color: #f8f9fa;
}

.m-upload-file-name {
	flex: 1;
	max-width: 200px;
	font-size: 14px;
	color: #333;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.m-upload-file-size {
	font-size: 12px;
	color: #666;
	margin: 0 12px;
	min-width: 80px;
}

.m-upload-file-type {
	max-width: 80px;
	font-size: 12px;
	color: #666;
	margin: 0 12px;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.m-upload-file-status {
	font-size: 12px;
	font-weight: 500;
	padding: 2px 8px;
	border-radius: 12px;
	min-width: 80px;
	text-align: center;
}

.m-upload-file-status-pending {
	background-color: #fff3cd;
	color: #856404;
}

.m-upload-file-status-processing {
	background-color: #cce5ff;
	color: #004085;
}

.m-upload-file-status-completed {
	background-color: #d4edda;
	color: #155724;
}

.m-upload-file-status-failed {
	background-color: #f8d7da;
	color: #721c24;
}

.m-upload-file-remove {
	background: none;
	border: none;
	font-size: 16px;
	font-weight: bold;
	color: #666;
	cursor: pointer;
	padding: 4px 8px;
	border-radius: 4px;
	transition: all 0.2s ease;
}

.m-upload-file-remove:hover {
	background-color: #e9ecef;
	color: #dc3545;
}

/* 响应式上传文件对话框 */
@media (max-width: 480px) {
	.m-upload-dropzone {
		padding: 30px 15px;
	}
	
	.m-upload-icon {
		font-size: 36px;
	}
	
	.m-upload-text {
		font-size: 14px;
	}
	
	.m-upload-btn {
		padding: 6px 12px;
		font-size: 13px;
	}
	
	.m-upload-file-item {
		flex-direction: column;
		align-items: flex-start;
		gap: 8px;
		padding: 12px;
	}
	
	.m-upload-file-size,
	.m-upload-file-type,
	.m-upload-file-status {
		margin: 0;
		min-width: auto;
	}
	
	.m-upload-file-remove {
		align-self: flex-end;
		margin-top: -28px;
	}
	
	.m-upload-files-header {
		display: none;
	}
}

/* 页面内通知样式 */
.m-alert-container {
	position: fixed;
	top: 20px;
	left: 50%;
	transform: translateX(-50%);
	max-width: 400px;
	z-index: 9999;
	box-sizing: border-box;
}

.m-alert-container .el-alert {
	margin-bottom: 10px;
	width: 100%;
}

/* 响应式页面内通知 */
@media (max-width: 480px) {
	.m-alert-container {
		top: 10px;
		left: 50%;
		transform: translateX(-50%);
		max-width: 90%;
	}
}
</style>