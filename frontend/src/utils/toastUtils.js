import { toast as sonner } from 'sonner';

// Repeated failures update one notification instead of stacking identical messages.
const notify = (kind, message, options = {}) => sonner[kind](message, { id: `${kind}:${message}`, ...options });
const toast = {
  error: (message, options) => notify('error', message, { duration: 6000, ...options }),
  success: (message, options) => notify('success', message, options),
  warning: (message, options) => notify('warning', message, { duration: 6000, ...options }),
  info: (message, options) => notify('info', message, options),
  dismiss: sonner.dismiss,
};
export default toast;
export const showWarningToast = toast.warning;
