import { useEffect, useMemo, useRef, useState } from 'react';
import { Background, Controls, ReactFlow, ReactFlowProvider, useNodesState, useReactFlow } from '@xyflow/react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { WIDE_COLUMNS, layoutSources } from '../../domain/layout';
import { useNarrowScreen } from './useNarrowScreen';
import type { Provider, Source } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import { AddMenu } from '../add-source/AddMenu';
import { PlaudDialog } from '../add-source/PlaudDialog';
import { providerName } from '../add-source/providerText';
import { UploadDialog } from '../add-source/UploadDialog';
import { SourceNode, type SourceFlowNode } from './nodes';
import { useWorkspace, type WorkspaceState } from './useWorkspace';

const NODE_TYPES = { source: SourceNode };
const FIT_VIEW = { maxZoom: 1, padding: 0.3 };
/** On a phone, fitting every node would shrink text below a readable size; people scroll instead. */
const NARROW_MIN_ZOOM = 0.85;
const DEFAULT_MIN_ZOOM = 0.5;

type OpenDialog = { kind: 'upload'; existing?: Source } | { kind: 'plaud' } | null;
type Ready = Extract<WorkspaceState, { status: 'ready' }>;

export function SourcesCanvas() {
  const t = useT();
  const { state, retry, refreshAfterCapture } = useWorkspace();
  const [dialog, setDialog] = useState<OpenDialog>(null);

  if (state.status === 'loading') {
    return <div className="canvas-state" role="status"><Icon name="sync" /> {t('canvas.loading')}</div>;
  }
  if (state.status === 'error') {
    return (
      <div className="canvas-state" role="alert">
        <div className="panel">
          <h2>{t('canvas.error.title')}</h2>
          <p>{t('canvas.error.body')}</p>
          <button type="button" className="btn primary" onClick={retry}>{t('canvas.retry')}</button>
        </div>
      </div>
    );
  }

  const chooseProvider = (provider: Provider) =>
    setDialog(provider.provider === 'PLAUD' ? { kind: 'plaud' } : { kind: 'upload' });

  return (
    <section className="viewport" aria-label={t('canvas.label')}>
      <ReactFlowProvider>
        <Flow workspace={state} onAddFiles={(source) => setDialog({ kind: 'upload', existing: source })} onSynced={refreshAfterCapture} />
      </ReactFlowProvider>
      <div className="canvas-tl"><AddMenu providers={state.providers} onChoose={chooseProvider} /></div>
      {state.sources.length === 0 && (
        <div className="empty">
          <h2>{t('canvas.empty.title')}</h2>
          <p>{t('canvas.empty.body')}</p>
        </div>
      )}
      {dialog?.kind === 'upload' && (
        <UploadDialog existing={dialog.existing} onClose={() => setDialog(null)} onCaptured={refreshAfterCapture} />
      )}
      {dialog?.kind === 'plaud' && <PlaudDialog onClose={() => setDialog(null)} onCaptured={refreshAfterCapture} />}
    </section>
  );
}

interface FlowProps {
  workspace: Ready;
  onAddFiles: (source: Source) => void;
  onSynced: () => void;
}

function Flow({ workspace, onAddFiles, onSynced }: FlowProps) {
  const t = useT();
  const [syncing, setSyncing] = useState<string | null>(null);
  const [imported, setImported] = useState<Record<string, number>>({});
  const [failed, setFailed] = useState<string | null>(null);
  const narrow = useNarrowScreen();

  async function sync(source: Source) {
    setSyncing(source.id);
    setFailed(null);
    try {
      const result = await api.sync(source.id);
      setImported((current) => ({ ...current, [source.id]: result.imported }));
      onSynced();
    } catch {
      setFailed(source.id);
    } finally {
      setSyncing(null);
    }
  }

  const providerLabels = useMemo(
    () => new Map(workspace.providers.map((provider) => [provider.provider, providerName(provider, t)])),
    [workspace.providers, t],
  );

  const target = useMemo<SourceFlowNode[]>(() => {
    const layout = layoutSources(workspace.sources, narrow ? 1 : WIDE_COLUMNS);
    return workspace.sources.map((source) => ({
      id: source.id,
      type: 'source',
      position: layout.get(source.id) ?? { x: 0, y: 0 },
      data: {
        source,
        label: providerLabels.get(source.provider) ?? source.provider,
        documentCount: workspace.documentCounts[source.id] ?? 0,
        syncing: syncing === source.id,
        lastImported: imported[source.id] ?? null,
        syncFailed: failed === source.id,
        onAddFiles: source.provider === 'UPLOAD' ? () => onAddFiles(source) : undefined,
        onSync: source.provider === 'PLAUD' ? () => void sync(source) : undefined,
      },
    }));
    // sync and onAddFiles change identity each render; the data they close over is listed.
  }, [workspace, providerLabels, syncing, imported, failed, narrow]);

  const [nodes, setNodes, onNodesChange] = useNodesState<SourceFlowNode>(target);
  const dragged = useRef(new Map<string, { x: number; y: number }>());
  const { fitView } = useReactFlow();
  const sourceCount = workspace.sources.length;

  useEffect(() => {
    // Keep where a person dragged a node; lay out everything else from the latest data.
    setNodes(target.map((node) => ({ ...node, position: dragged.current.get(node.id) ?? node.position })));
  }, [target, setNodes]);

  useEffect(() => {
    // Bring a newly added source into view once React Flow has placed it.
    const frame = window.requestAnimationFrame(() => void fitView(FIT_VIEW));
    return () => window.cancelAnimationFrame(frame);
  }, [sourceCount, narrow, fitView]);

  useEffect(() => {
    const refit = () => void fitView(FIT_VIEW);
    window.addEventListener('resize', refit);
    return () => window.removeEventListener('resize', refit);
  }, [fitView]);

  const ariaLabels = useMemo(() => ({
    'controls.ariaLabel': t('canvas.controls'),
    'controls.zoomIn.ariaLabel': t('canvas.zoomIn'),
    'controls.zoomOut.ariaLabel': t('canvas.zoomOut'),
    'controls.fitView.ariaLabel': t('canvas.fitView'),
    'controls.interactive.ariaLabel': t('canvas.interactive'),
  }), [t]);


  return (
    <ReactFlow
      nodes={nodes}
      nodeTypes={NODE_TYPES}
      onNodesChange={onNodesChange}
      onNodeDragStop={(_, node) => dragged.current.set(node.id, node.position)}
      nodesConnectable={false}
      fitView
      fitViewOptions={FIT_VIEW}
      minZoom={narrow ? NARROW_MIN_ZOOM : DEFAULT_MIN_ZOOM}
      ariaLabelConfig={ariaLabels}
    >
      <Background gap={22} size={1.4} />
      <Controls showInteractive={false} position={narrow ? 'bottom-right' : 'bottom-left'} />
    </ReactFlow>
  );
}
