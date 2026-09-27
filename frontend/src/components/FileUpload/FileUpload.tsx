import { useState } from 'react';
import { App, Upload } from 'antd';
import type { UploadFile, UploadProps } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { StagedUpload } from '../../types/file';
import { deleteStagedFile, uploadStagedFile } from '../../api/file';
import { getApiErrorMessage } from '../../utils/apiError';

export interface FileUploadProps {
  value?: StagedUpload;
  onChange?: (value?: StagedUpload) => void;
  accept?: string;
  maxBytes?: number;
  disabled?: boolean;
  hint?: string;
}

/**
 * 通用“单文件暂存”组件。业务表单只保存 token，提交导入时再把 token 交给对应业务接口。
 */
export function FileUpload({ value, onChange, accept = '.csv,.xlsx', maxBytes = 10 * 1024 * 1024, disabled, hint }: FileUploadProps) {
  const { message } = App.useApp();
  const [uploading, setUploading] = useState(false);
  const fileList: UploadFile[] = value ? [{ uid: value.token, name: value.originalName, status: 'done', size: value.size, type: value.contentType }] : [];

  const customRequest: UploadProps['customRequest'] = async ({ file, onProgress, onSuccess, onError }) => {
    if (!(file instanceof File)) return;
    try {
      setUploading(true);
      const result = await uploadStagedFile(file, (percent) => onProgress?.({ percent }));
      onChange?.(result);
      onSuccess?.(result);
    } catch (error) {
      message.error(getApiErrorMessage(error, '文件上传失败'));
      onError?.(error as Error);
    } finally {
      setUploading(false);
    }
  };

  return (
    <Upload.Dragger
      accept={accept}
      maxCount={1}
      disabled={disabled || uploading}
      fileList={fileList}
      customRequest={customRequest}
      beforeUpload={(file) => {
        if (file.size <= maxBytes) return true;
        message.error(`文件大小不能超过 ${Math.floor(maxBytes / 1024 / 1024)} MB`);
        return Upload.LIST_IGNORE;
      }}
      onRemove={async () => {
        if (value) {
          try { await deleteStagedFile(value.token); }
          catch (error) { message.error(getApiErrorMessage(error, '暂存文件删除失败')); return false; }
        }
        onChange?.(undefined);
        return true;
      }}
    >
      <p className='ant-upload-drag-icon'><InboxOutlined /></p>
      <p className='ant-upload-text'>点击或拖拽文件到这里上传</p>
      <p className='ant-upload-hint'>{hint ?? '支持 CSV、XLSX，单个文件不超过 10 MB'}</p>
    </Upload.Dragger>
  );
}
