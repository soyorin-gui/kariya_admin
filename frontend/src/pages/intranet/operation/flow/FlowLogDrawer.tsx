import { useEffect, useState } from 'react';
import { App, Button, Col, Descriptions, Drawer, List, Row, Skeleton, Space, Tag, Typography } from 'antd';
import { DownloadOutlined, FileTextOutlined } from '@ant-design/icons';
import { CodeViewer } from '../../../../components/CodeViewer';
import { getApiErrorMessage } from '../../../../utils/apiError';
import { downloadBlob } from '../../../../utils/download';
import { downloadFlowLog, getFlowLogContext } from './api';
import type { FlowLogContext, TransactionFlowRecord } from './types';

export function FlowLogDrawer({ environmentId, flow, onClose }: { environmentId?: string; flow?: TransactionFlowRecord; onClose: () => void }) {
  const { message } = App.useApp();
  const [context, setContext] = useState<FlowLogContext>();
  const [loading, setLoading] = useState(false);
  const [downloadingFileId, setDownloadingFileId] = useState<string>();

  useEffect(() => {
    if (!flow || !environmentId) {
      setContext(undefined);
      return;
    }
    let active = true;
    setContext(undefined);
    setLoading(true);
    void getFlowLogContext(environmentId, flow)
      .then((result) => active && setContext(result))
      .catch((error) => active && message.error(getApiErrorMessage(error, '无法加载流水日志')))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [environmentId, flow, message]);

  const download = async (file: NonNullable<FlowLogContext>['files'][number]) => {
    if (!environmentId) return;
    setDownloadingFileId(file.fileId);
    try {
      downloadBlob(await downloadFlowLog(environmentId, file), file.fileName);
    } catch (error) {
      message.error(getApiErrorMessage(error, '无法下载日志文件'));
    } finally {
      setDownloadingFileId(undefined);
    }
  };

  return (
    <Drawer title={flow ? `日志追踪 · ${flow.globalFlowId}` : '日志追踪'} width='82vw' open={Boolean(flow)} onClose={onClose} destroyOnClose>
      {loading ? (
        <Skeleton active paragraph={{ rows: 16 }} />
      ) : (
        <Space direction='vertical' size='large' style={{ width: '100%' }}>
          <Row gutter={[24, 24]}>
            <Col xs={24} xl={12}>
              {flow && (
                <Descriptions bordered size='small' column={1} title='流水信息'>
                  <Descriptions.Item label='交易码'>{flow.transactionCode}</Descriptions.Item>
                  <Descriptions.Item label='调用方'>{flow.callerId}</Descriptions.Item>
                  <Descriptions.Item label='服务地址'>{flow.serviceAddress}</Descriptions.Item>
                  <Descriptions.Item label='开始时间'>{flow.startTime}</Descriptions.Item>
                  <Descriptions.Item label='结束时间'>{flow.endTime}</Descriptions.Item>
                  <Descriptions.Item label='响应状态'><Tag color={flow.responseStatus === 'Y' ? 'success' : flow.responseStatus === 'N' ? 'warning' : 'error'}>{flow.responseStatus}</Tag></Descriptions.Item>
                </Descriptions>
              )}
            </Col>
            <Col xs={24} xl={12}>
              <Typography.Title level={5}>相关日志文件</Typography.Title>
              <List
                bordered
                dataSource={context?.files ?? []}
                locale={{ emptyText: '未找到相关日志文件' }}
                renderItem={(file) => (
                  <List.Item
                    actions={[
                      <Button key='download' type='link' icon={<DownloadOutlined />} loading={downloadingFileId === file.fileId} onClick={() => void download(file)}>
                        下载
                      </Button>,
                    ]}
                  >
                    <List.Item.Meta
                      avatar={<FileTextOutlined />}
                      title={<Space><span>{file.fileName}</span><Tag color={file.fileType === 'CURRENT' ? 'processing' : 'default'}>{file.fileType === 'CURRENT' ? '当前日志' : '滚动日志'}</Tag>{file.truncated && <Tag color='warning'>已截断</Tag>}</Space>}
                      description={`${file.lastModified} · ${file.fileSize} B`}
                    />
                  </List.Item>
                )}
              />
            </Col>
          </Row>
          {context?.warnings.map((warning) => <Typography.Text key={warning} type='warning'>{warning}</Typography.Text>)}
          <div>
            <Typography.Title level={5}>匹配日志内容</Typography.Title>
            <CodeViewer value={context?.content ?? ''} filename={`${flow?.globalFlowId ?? 'flow'}-logs.txt`} height={420} />
          </div>
        </Space>
      )}
    </Drawer>
  );
}
