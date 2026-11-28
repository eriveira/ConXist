import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './styles/app.css';
import './styles/source.css';
import './styles/shell.css';
import './styles/agents.css';
import './styles/skills.css';
import './styles/settings.css';
import './styles/tuga.css';
import { App } from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
