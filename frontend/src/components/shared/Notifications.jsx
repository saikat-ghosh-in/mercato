import { Toaster } from 'sonner';
import { FiCheckCircle, FiAlertCircle, FiAlertTriangle, FiInfo } from 'react-icons/fi';
import { Spinner } from './Spinner';
import { useEffect } from 'react';
import { useSelector } from 'react-redux';
import toast from '../../utils/toastUtils';

export default function Notifications() {
  const epoch = useSelector(state => state.auth.sessionEpoch);
  useEffect(() => { toast.dismiss(); }, [epoch]);
  return <Toaster theme="light" position="bottom-right" visibleToasts={3} gap={8} offset={20} mobileOffset={{ left: 12, right: 12, bottom: 16 }}
    icons={{ success: <FiCheckCircle />, error: <FiAlertCircle />, warning: <FiAlertTriangle />, info: <FiInfo />, loading: <Spinner /> }}
    toastOptions={{ className: 'mercato-toast', duration: 4000, classNames: { title: 'mercato-toast-title', description: 'mercato-toast-description' } }} />;
}
