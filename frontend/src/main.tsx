import React from 'react';
import ReactDOM from 'react-dom/client';
import { App as AntApp } from 'antd';
import { Provider } from 'react-redux';
import { RouterProvider } from 'react-router-dom';
import { store } from './store';
import { router } from './router';
import { ThemeProvider } from './theme/ThemeProvider';
// nprogress 的基础样式（默认色 #29d）。必须排在自己的样式之前引入，
// 这样 styles/global.css 里的品牌色覆盖才处于后者、优先级明确。
import 'nprogress/nprogress.css';
import './styles/global.css';
import './layouts/layout.css';
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <ThemeProvider>
        <AntApp>
          <RouterProvider router={router} />
        </AntApp>
      </ThemeProvider>
    </Provider>
  </React.StrictMode>,
);
